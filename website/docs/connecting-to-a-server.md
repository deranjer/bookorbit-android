---
sidebar_position: 3
---

# Connecting to a server

BookOrbit is a client for your own **self-hosted** BookOrbit server — the app doesn't talk to
anything operated by the developer. On first launch, you'll be asked for your server's URL.

## Server URL

Enter the full address of your BookOrbit server, including the scheme, for example:

- `https://books.example.com` for a server exposed over HTTPS
- `http://192.168.1.50:3000` for a server on your local network

Cleartext HTTP is permitted (self-hosted servers are often plain HTTP on a LAN), but prefer HTTPS
whenever your server is reachable from outside your network.

### Connecting to a local dev/test server

If you're running a BookOrbit server locally for development or testing:

- **Emulator:** the host machine is reachable at `10.0.2.2`, so enter `http://10.0.2.2:3000`.
- **Physical device:** use your machine's LAN IP, e.g. `http://192.168.1.50:3000`.

## Signing in

Once the server URL is set, sign in with:

- **Username and password**, if your server uses local accounts, or
- **OIDC / SSO**, if your server administrator has configured an identity provider.

For OIDC to work from the mobile app, the server must whitelist the mobile redirect URI
`bookorbit://oauth2-callback` (the `OIDC_MOBILE_REDIRECT_URIS` setting on the server, which
includes this by default).

## Switching servers

You can sign out and connect to a different server at any time from the app's account settings.
Signing out clears locally cached credentials but does not affect any data stored on the server.
