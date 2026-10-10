"""Write cloud-local Gradle configuration using the injected proxy, never credentials."""
import os
from pathlib import Path
from urllib.parse import urlsplit

proxy = urlsplit(os.environ.get("HTTPS_PROXY", os.environ.get("https_proxy", "")))
if proxy.username or proxy.password:
    raise SystemExit("Credential-bearing proxies require a secure binding; refusing to persist credentials.")
root = Path(os.environ["GRADLE_USER_HOME"])
root.mkdir(parents=True, exist_ok=True)
lines = ["org.gradle.workers.max=2", "org.gradle.jvmargs=-Xmx2g -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"]
if proxy.hostname:
    for protocol in ("http", "https"):
        lines += [f"systemProp.{protocol}.proxyHost={proxy.hostname}", f"systemProp.{protocol}.proxyPort={proxy.port or 80}"]
lines += ["systemProp.http.nonProxyHosts=localhost|127.*"]
(root / "gradle.properties").write_text("\n".join(lines) + "\n")
