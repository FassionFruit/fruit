
# Security Policy

## Supported Versions

FassionFruit is currently under active development and has not established a stable release or formal long-term support policy.

Unless otherwise stated, security fixes will target the latest version available on the `main` branch.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues or pull requests.**

Use GitHub's private vulnerability reporting feature:

[Report a Vulnerability](https://github.com/FassionFruit/fruit/security/advisories/new)

When reporting a vulnerability, please include:

- A description of the vulnerability.
- Potential security impact.
- Affected versions or commits.
- Steps to reproduce the issue.
- Relevant JDK, operating system, and runtime information.
- A suggested mitigation, if available.

Please avoid including unrelated sensitive information or real secrets.

If private vulnerability reporting is unavailable, contact the project maintainers privately through the GitHub organization.

## Response Process

The maintainers will:

1. Review the submitted report.
2. Attempt to reproduce the vulnerability.
3. Assess its severity and impact.
4. Determine affected versions.
5. Coordinate a fix and disclosure timeline where possible.

Response and remediation times are not guaranteed while the project is maintained on a best-effort basis.

## Security Considerations

FassionFruit interacts directly with native code through Java's Foreign Function & Memory API.

Users and contributors should be aware of the following:

- Native libraries execute with the privileges of the hosting process.
- Loading untrusted native libraries can compromise the application.
- Incorrect memory layouts or pointer handling may cause memory corruption or crashes.
- Memory segments must not be accessed after their owning arena has been closed.
- Native callbacks must respect the lifetime of their associated memory and resources.
- Native access should only be enabled for code that you trust.

FassionFruit does not provide isolation or sandboxing for native libraries.

Applications are responsible for validating native libraries and managing native memory safely.
