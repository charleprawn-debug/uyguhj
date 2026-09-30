# Android VPN DNS and notification input references

Sources checked for the KUN Proxy VPN and advanced-pairing work:

- Android Developers, **Create a notification** (Direct reply / `RemoteInput`): https://developer.android.com/develop/ui/compose/notifications/create-notification
  - Attach `RemoteInput` to a notification action; send the action through a `PendingIntent` targeting a receiver; read text with `RemoteInput.getResultsFromIntent(intent)`; refresh the same notification after processing.
- sing-box, **Rule Action** (`hijack-dns`): https://sing-box.sagernet.org/configuration/route/rule_action/
  - The exact final action is `{ "action": "hijack-dns" }`, which directs DNS requests into the sing-box DNS module.
- sing-box, **Route Rule**: https://sing-box.sagernet.org/configuration/route/rule/
  - Provides the route-rule schema for applying DNS interception before the final proxy route.
- sing-box, **TUN inbound**: https://sing-box.sagernet.org/configuration/inbound/tun/
  - `auto_route` installs routes for TUN traffic. The embedded `libbox.aar` version must be considered before using newer options such as `dns_mode`.
- sing-box, **Route**: https://sing-box.sagernet.org/configuration/route/
  - The current route reference limits generic `auto_detect_interface` to Linux, Windows, and macOS. It is disabled in KUN Proxy's Android VPN configuration.
- sing-box for Android, **VPNService**: https://github.com/SagerNet/sing-box-for-android/blob/dev/app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt
  - The upstream Android client protects sing-box outbound sockets through `VpnService.protect(fd)` in `autoDetectInterfaceControl`; KUN Proxy follows that Android platform mechanism to keep upstream sockets outside its own VPN.

These sources are implementation references, not a guarantee that a particular upstream proxy accepts the configured protocol or credentials; the app should test the upstream handshake before enabling full-device routing.
