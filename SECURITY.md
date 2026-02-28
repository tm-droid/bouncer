# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| Latest (`main`) | ✅ |
| Older releases | ❌ |

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

If you discover a security vulnerability in Bouncer, please report it privately:

1. Email **tanushmcoding@gmail.com** with the subject line: `[SECURITY] Bouncer — <brief description>`
2. Include:
   - A description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fix (optional)

You can expect an acknowledgement within **48 hours** and a full response within **7 days**.

## Scope

Areas of particular concern:

- API key exposure or leakage via APK / network traffic
- Insecure data storage (SharedPreferences, local DB)
- Network interception / man-in-the-middle vulnerabilities
- Improper WebView usage (if any)

## Out of Scope

- Issues in third-party libraries (report upstream)
- Social engineering
- Physical device access scenarios

## Responsible Disclosure

We appreciate responsible disclosure. Once a fix is released, you are welcome to publish your findings — please coordinate the timing with us.
