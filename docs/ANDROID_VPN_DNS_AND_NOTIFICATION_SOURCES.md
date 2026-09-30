# Android VPN DNS and notification input references

Sources checked for the KUN Proxy VPN and advanced-pairing work:

- Android Developers, **Create a notification** (Direct reply / `RemoteInput`): https://developer.android.com/develop/ui/compose/notifications/create-notification
  - Attach `RemoteInput` to a notification action; send the action through a `PendingIntent` targeting a receiver; read text with `RemoteInput.getResultsFromIntent(intent)`; refresh the same notification after processing.
- sing-box, **Rule Action** (`hijack-dns`): https://sing-box.sagernet.org/configuration/route/rule_action/
  - The exact final action is `{ "action": "hijack-dns" }`, which directs DNS requests into the sing-box DNS module.
- sing-box, **Route Rule**: https://sing-box.sagernet.org/configuration/route/rule/
  - Provides the route-rule schema for applying DNS interception before the final proxy route.
- sing-box, **TUN inbound**: https://sing-box.sagernet.org/configuration/inbound/tun/
  - `auto_route` installs default routes; `auto_detect_interface` (or an explicit underlying interface) is needed to avoid outbound proxy traffic looping into the tunnel. Newer options such as `dns_mode` have documented version requirements, so the embedded `libbox.aar` version must be considered before using them.

These sources are implementation references, not a guarantee that a particular upstream proxy accepts the configured protocol or credentials; the app should test the upstream handshake before enabling full-device routing.
