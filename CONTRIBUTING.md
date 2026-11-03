
# Contributing to FassionFruit

Thank you for your interest in contributing to FassionFruit!

FassionFruit is an open-source project focused on simplifying native interoperability through Java's Foreign Function & Memory API.

We welcome bug reports, feature suggestions, documentation improvements, and code contributions.

## Getting Started

Before contributing:

- Check existing issues and pull requests.
- Discuss significant changes before starting implementation.
- Keep contributions focused and avoid unrelated changes.

## Development Environment

The project requires:

- JDK 25
- Gradle Wrapper
- A C compiler for native integration tests

Clone the repository:

```bash
git clone https://github.com/FassionFruit/fruit.git
cd fruit
```

Build the project:

```bash
./gradlew build
```

## Contribution Guidelines

### Code Quality

- Follow the existing code style and project structure.
- Keep implementations simple and maintainable.
- Avoid unnecessary dependencies.
- Provide clear error messages for invalid declarations.
- Avoid introducing unrelated changes.

### API Design

FassionFruit aims to provide a lightweight and intuitive API.

When proposing API changes:

- Prefer simple and consistent interfaces.
- Avoid unnecessary abstractions.
- Consider both Java usability and native interoperability.
- Document native ABI assumptions and limitations.
- Consider backward compatibility.

### Native Interoperability

Changes involving native memory require particular care.

- Respect memory segment and arena lifetimes.
- Consider native alignment and memory layout.
- Validate native type conversions.
- Handle invalid pointers and unsupported types appropriately.
- Add native test fixtures when necessary.

## Testing

All contributions should include appropriate tests.

Run the full test suite before submitting a pull request:

```bash
./gradlew build
```

For native interoperability changes, include integration tests whenever practical.

Tests should cover:

- Expected behavior
- Invalid declarations
- Unsupported types
- Boundary conditions
- Native memory handling

## Use of AI Tools

The use of AI tools is permitted when contributing to FassionFruit.

However, contributors are fully responsible for all contributions they submit, including any code or content generated with the assistance of AI tools.

Contributors must ensure that:

- Submitted code is understood and can be properly explained.
- Generated code is reviewed, tested, and verified before submission.
- AI-generated content does not introduce security vulnerabilities, license violations, or unrelated changes.
- All contributions comply with this project's guidelines and license.

AI assistance does not exempt contributors from responsibility for the quality, correctness, and maintainability of their contributions.

## Pull Requests

1. Fork the repository.
2. Create a branch for your changes.
3. Implement your changes and add tests.
4. Run the project build and test suite.
5. Submit a pull request against the `main` branch.

A pull request should include:

- A clear description of the changes.
- The motivation behind the changes.
- Relevant test results.
- Any API or compatibility implications.

Keep pull requests focused and reasonably sized.

## Reporting Security Issues

Do not report security vulnerabilities through public issues or pull requests.

Please follow the instructions in [SECURITY.md](SECURITY.md).

## License

By submitting a contribution, you agree that your contributions will be licensed under the Apache License 2.0.
