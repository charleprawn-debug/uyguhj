# Network transport audit

## Implemented

The Android client now embeds the official Sing-box `libbox.aar` built from Sing-box v1.14.0 source with the Android arm64 target. The old `tun2socks` dependency and `engine.Engine` path have been removed.

`ProxyVpnService` initializes `Libbox`, starts a `CommandServer`, writes a runtime `Config.json` into the app's private files directory, and starts/reloads Sing-box with the configured HTTP or SOCKS5 upstream. `SingBoxPlatformInterface` implements the Android platform bridge. Sing-box owns the upstream sockets and calls `VpnService.protect(fd)` through `autoDetectInterfaceControl`, preventing recursive capture.

The runtime configuration includes an HTTPS DNS server at `1.1.1.1/dns-query` with the DNS connection detoured through the proxy, a TUN inbound with `auto_route: true` and `strict_route: true`, `route.auto_detect_interface: true`, IPv4/IPv6 TUN addresses, and default routing through the proxy.

The notification uses a versioned `IMPORTANCE_MIN` channel, a valid transparent vector resource, a silent minimum-priority notification, and a UI action opening that exact system channel. Android still controls the system VPN indicator; no app can guarantee removal of that indicator while a VpnService is active.

## Multiplexing compatibility

Sing-box's official schema rejects `multiplex` on SOCKS and HTTP outbounds. The runtime config therefore does not insert an invalid field for the two protocols exposed by the current UI. The generator adds Sing-box Mux only for protocols that expose `OutboundMultiplexOptions` (`vmess`, `vless`, `trojan`, and `shadowsocks`) if those protocols are added to the UI later. This behavior was confirmed by `sing-box check`.

## Verification

The following checks completed successfully:

| Check | Result |
|---|---|
| Official libbox build | `libbox.aar` generated successfully from Sing-box source |
| Sing-box config schema | `go run ./cmd/sing-box check -c validation-config.json` passed |
| Android compilation | `./gradlew clean assembleDebug --no-daemon` passed |
| APK native payload | `lib/arm64-v8a/libbox.so` present in the APK |
| Runtime config asset | `assets/Config.json` packaged in the APK |

The generated debug APK targets arm64-v8a because that is the native libbox artifact built and embedded in this revision. A multi-ABI release requires building and merging additional libbox AAR artifacts for `armeabi-v7a`, `x86`, and `x86_64`.

Physical-device validation is still required for Android OEM behavior, network handover, DNS leak testing, and real upstream connectivity. The sandbox can validate compilation and schema, but cannot certify those device-level runtime properties without an Android device.

## CI validation

The Mock Location implementation deliberately does not declare `ACCESS_MOCK_LOCATION`; Android grants test-provider control after the user selects the application in Developer options, while Lint rejects that permission declaration even in a debug manifest. The provider uses the Android `ProviderProperties` constants required by current SDK lint checks. The GitHub Actions command `lintDebug testDebugUnitTest assembleDebug --no-daemon` passes locally after this correction.

## Proxy-matched Mock GPS

The app now includes an explicit, user-controlled Mock Location option. In automatic mode it sends an HTTPS GeoIP request to `ipwho.is` through the configured HTTP or SOCKS5 proxy and publishes the returned latitude and longitude through an Android test provider every 15 minutes. In manual mode the user supplies latitude and longitude directly. The controller stops and removes the test provider when the proxy is stopped.

Android requires the user to enable Developer options and select **Proxy Platform** as the mock-location app. The application does not bypass this Android security gate, does not alter hardware GPS, and does not attempt to evade mock-location detection. Some applications may ignore mock locations or detect them using Play Integrity, sensors, or their own telemetry. The GeoIP result is approximate and represents the proxy exit location as reported by the provider, not a guarantee of physical presence.

The UI provides a direct button to open Developer options, automatic/manual mode selection, coordinate validation, and lifecycle cleanup. A physical Android-device test is still required because OEMs may restrict mock providers differently.
