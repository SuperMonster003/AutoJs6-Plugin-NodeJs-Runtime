Local OpenSSL 3.5.9 compatibility and security-update regression fixtures.

The private keys are disposable test credentials. Their CA is trusted only by
the explicit loopback clients in this test. Certificates are valid from
2020-01-01 to 2050-01-01. The SNI certificate includes a relative CRLDP name.

The tests cover TLS 1.2/1.3, RSA-to-EC SNI context switching, certificate/hostname
rejection, Brainpool/NIST signing and ECDH, RSA-PSS, AES-GCM authentication and
WebCrypto, through CJS, ESM, native Workers and the terminal launcher.
Functional signing tests do not establish constant-time behavior. The security
fixes come from the recorded upstream source/architecture update in Node PR 66396.

relative-crldp-memory.pem is a self-signed, 72,968-byte regression certificate
with 256 issuer OU attributes and 1,024 relative distribution points. No network
or external certificate service is involved. In a fresh terminal process,
crldp-memory.cjs checks that reading its CA flag does not eagerly expand the
issuer name for every distribution point (CVE-2026-35189). The 32 MiB RSS-growth
ceiling leaves substantial allocator headroom over the fixed implementation.
