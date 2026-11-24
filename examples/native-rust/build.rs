use quote::ToTokens;
use serde_json::{json, Value};
use std::collections::HashSet;
use std::env;
use std::fs;
use std::path::PathBuf;
use syn::{Fields, FnArg, Item, ReturnType, Type};

fn main() {
    println!("cargo:rerun-if-changed=src/lib.rs");
    println!("cargo:rerun-if-env-changed=FASSIONFRUIT_METADATA_DIR");

    let source = fs::read_to_string("src/lib.rs")
        .expect("failed to read native Rust source");
    let syntax = syn::parse_file(&source)
        .expect("failed to parse native Rust source for FassionFruit metadata");

    let structure_names: HashSet<String> = syntax.items.iter().filter_map(|item| match item {
        Item::Struct(value) if has_repr_c(&value.attrs) => Some(value.ident.to_string()),
        Item::Union(value) if has_repr_c(&value.attrs) => Some(value.ident.to_string()),
        _ => None,
    }).collect();

    let mut functions = Vec::new();
    let mut structures = Vec::new();

    for item in &syntax.items {
        match item {
            Item::Fn(function) => {
                let Some(abi) = &function.sig.abi else { continue };
                if abi.name.as_ref().map(|name| name.value()).as_deref() != Some("C")
                    || function.sig.variadic.is_some()
                {
                    continue;
                }
                if !matches!(&function.vis, syn::Visibility::Public(_)) {
                    continue;
                }
                if !function.attrs.iter().any(|attribute| {
            attribute.path().is_ident("no_mangle")
                || attribute.meta.to_token_stream().to_string().contains("no_mangle")
        }) {
                    continue;
                }

                let mut parameters = Vec::new();
                let mut supported = true;
                for argument in &function.sig.inputs {
                    let FnArg::Typed(argument) = argument else {
                        supported = false;
                        break;
                    };
                    let name = match argument.pat.as_ref() {
                        syn::Pat::Ident(identifier) => identifier.ident.to_string(),
                        _ => {
                            supported = false;
                            break;
                        }
                    };
                    let Some(parameter_type) = map_type(&argument.ty, &structure_names) else {
                        supported = false;
                        break;
                    };
                    parameters.push(json!({ "name": name, "type": parameter_type }));
                }

                let return_type = match &function.sig.output {
                    ReturnType::Default => Some("void".to_owned()),
                    ReturnType::Type(_, ty) => map_type(ty, &structure_names),
                };
                let Some(return_type) = return_type else {
                    println!("cargo:warning=FassionFruit skipped unsupported return type for {}", function.sig.ident);
                    continue;
                };
                if !supported {
                    println!("cargo:warning=FassionFruit skipped unsupported export {}", function.sig.ident);
                    continue;
                }

                let name = function.sig.ident.to_string();
                functions.push(json!({
                    "name": name,
                    "symbol": name,
                    "abi": "C",
                    "return": return_type,
                    "parameters": parameters
                }));
            }
            Item::Struct(value) if has_repr_c(&value.attrs) => {
                let source_fields: Vec<_> = match &value.fields {
                    Fields::Named(fields) => fields.named.iter().collect(),
                    Fields::Unnamed(fields) => fields.unnamed.iter().collect(),
                    Fields::Unit => Vec::new(),
                };
                let fields = source_fields.iter().enumerate().map(|(index, field)| {
                    let name = field.ident.as_ref().map(ToString::to_string).unwrap_or_else(|| format!("_{index}"));
                    let field_type = map_type(&field.ty, &structure_names).unwrap_or_else(|| "unsupported".to_owned());
                    json!({ "name": name, "type": field_type })
                }).collect::<Vec<_>>();
                structures.push(json!({
                    "name": value.ident.to_string(),
                    "kind": "struct",
                    "representation": "C",
                    "packed": is_packed(&value.attrs),
                    "fields": fields
                }));
            }
            Item::Union(value) if has_repr_c(&value.attrs) => {
                let fields = value.fields.named.iter().map(|field| {
                    let name = field.ident.as_ref().map(ToString::to_string).unwrap_or_default();
                    let field_type = map_type(&field.ty, &structure_names).unwrap_or_else(|| "unsupported".to_owned());
                    json!({ "name": name, "type": field_type })
                }).collect::<Vec<_>>();
                structures.push(json!({
                    "name": value.ident.to_string(),
                    "kind": "union",
                    "representation": "C",
                    "packed": is_packed(&value.attrs),
                    "fields": fields
                }));
            }
            _ => {}
        }
    }

    let metadata: Value = json!({
        "version": 1,
        "abi": "C",
        "target": env::var("TARGET").unwrap_or_default(),
        "pointerWidth": env::var("CARGO_CFG_TARGET_POINTER_WIDTH")
            .ok()
            .and_then(|value| value.parse::<u32>().ok())
            .unwrap_or(0),
        "library": env::var("CARGO_CRATE_NAME")
            .or_else(|_| env::var("CARGO_PKG_NAME"))
            .expect("missing Cargo crate name"),
        "functions": functions,
        "structures": structures
    });
    let output = env::var_os("FASSIONFRUIT_METADATA_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(env::var_os("OUT_DIR").expect("missing OUT_DIR")));
    fs::create_dir_all(&output).expect("failed to create FassionFruit metadata directory");
    let crate_name = env::var("CARGO_CRATE_NAME")
        .or_else(|_| env::var("CARGO_PKG_NAME"))
        .unwrap();
    let file = output.join(format!("{crate_name}.ffm.json"));
    fs::write(file, serde_json::to_vec_pretty(&metadata).unwrap())
        .expect("failed to write FassionFruit metadata");
}

fn has_repr_c(attributes: &[syn::Attribute]) -> bool {
    attributes.iter().any(|attribute| {
        if !attribute.path().is_ident("repr") {
            return false;
        }
        let repr = attribute.meta.to_token_stream().to_string().replace(' ', "");
        if !repr.contains("repr(C") || repr.contains("align(") {
            return false;
        }
        if let Some(value) = repr.split("packed(").nth(1).and_then(|value| value.split(')').next()) {
            return value == "1";
        }
        true
    })
}

fn is_packed(attributes: &[syn::Attribute]) -> bool {
    attributes.iter().any(|attribute| {
        attribute.path().is_ident("repr")
            && attribute.meta.to_token_stream().to_string().replace(' ', "").contains("packed")
    })
}

fn map_type(ty: &Type, structures: &HashSet<String>) -> Option<String> {
    match ty {
        Type::Tuple(tuple) if tuple.elems.is_empty() => Some("void".to_owned()),
        Type::Path(path) => {
            let name = path.path.segments.last()?.ident.to_string();
            match name.as_str() {
                "i8" | "u8" => Some("i8".to_owned()),
                "i16" | "u16" => Some("i16".to_owned()),
                "i32" | "u32" => Some("i32".to_owned()),
                "i64" | "u64" => Some("i64".to_owned()),
                "f32" => Some("f32".to_owned()),
                "f64" => Some("f64".to_owned()),
                "c_char" => Some("i8".to_owned()),
                "c_void" => Some("void".to_owned()),
                _ if structures.contains(&name) => Some(format!("struct:{name}")),
                _ => None,
            }
        }
        Type::Ptr(pointer) => {
            if pointer.mutability.is_none() {
                if let Type::Path(path) = pointer.elem.as_ref() {
                    let name = path.path.segments.last()?.ident.to_string();
                    if name == "c_char" {
                        return Some("string".to_owned());
                    }
                }
            }
            let pointee = map_type(&pointer.elem, structures).unwrap_or_else(|| "opaque".to_owned());
            Some(format!("pointer<{pointee}>"))
        }
        Type::Reference(reference) => {
            let pointee = map_type(&reference.elem, structures).unwrap_or_else(|| "opaque".to_owned());
            Some(format!("pointer<{pointee}>"))
        }
        Type::Array(array) => {
            let element = map_type(&array.elem, structures)?;
            Some(format!("array<{element},{}>", array.len.to_token_stream()))
        }
        _ => None,
    }
}
