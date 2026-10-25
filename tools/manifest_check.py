#!/usr/bin/env python3
"""Security checks on the merged release manifest. Prints one line per finding; exit 1 on any."""
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ALLOWED_PERMISSIONS = {
    "android.permission.RECORD_AUDIO",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    # Added by the Google Mobile Ads SDK / WorkManager / AndroidX; none touches user files or sensors.
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
    "android.permission.ACCESS_ADSERVICES_TOPICS",
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
}
# Exported components other than the launcher must be protected by a signature/system permission.
ALLOWED_EXPORTED = {
    "com.mouthband.MainActivity": None,
    "androidx.work.impl.background.systemjob.SystemJobService": "android.permission.BIND_JOB_SERVICE",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver": "android.permission.DUMP",
    "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
}
# Components that send data to third parties on their own; none may be in the merged manifest.
FORBIDDEN_COMPONENTS = {
    "ai.onnxruntime.TelemetryInitializer",  # ONNX Runtime's 1DS telemetry, started at app start
}
FORBIDDEN_FRAGMENTS = (
    "STORAGE", "MEDIA", "LOCATION", "CAMERA", "CONTACTS", "SMS", "PHONE", "CALL_LOG", "CALENDAR",
    "BLUETOOTH", "BODY_SENSORS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "QUERY_ALL_PACKAGES",
)


def attr(element, name):
    return element.attrib.get(ANDROID + name)


def main(path, backup_dir):
    problems = []
    root = ET.parse(path).getroot()
    app = root.find("application")

    for element in root.findall("uses-permission"):
        name = attr(element, "name")
        if name.startswith("com.mouthband.app."):
            continue  # the app's own dynamic-receiver permission
        if name not in ALLOWED_PERMISSIONS:
            problems.append(f"permission not on the allow list: {name}")
        if any(f in name for f in FORBIDDEN_FRAGMENTS):
            problems.append(f"forbidden permission: {name}")

    if attr(app, "debuggable") == "true":
        problems.append("release is debuggable")
    if attr(app, "usesCleartextTraffic") != "false":
        problems.append("usesCleartextTraffic is not explicitly false")
    if attr(app, "networkSecurityConfig") is not None:
        problems.append("a custom network security config is present (review it)")

    for element in app:
        if element.tag not in ("activity", "service", "receiver", "provider", "activity-alias"):
            continue
        name = attr(element, "name")
        exported = attr(element, "exported")
        has_filter = element.find("intent-filter") is not None
        if exported is None and has_filter:
            problems.append(f"{element.tag} {name}: intent filter without explicit android:exported")
        if exported == "true":
            if name not in ALLOWED_EXPORTED:
                problems.append(f"unexpected exported {element.tag}: {name}")
            elif attr(element, "permission") != ALLOWED_EXPORTED[name]:
                problems.append(f"exported {name} is not protected by {ALLOWED_EXPORTED[name]}")
        if name in FORBIDDEN_COMPONENTS:
            problems.append(f"telemetry component {name} is in the manifest")
        if element.tag == "provider" and name == "androidx.core.content.FileProvider":
            if exported != "false" or attr(element, "grantUriPermissions") != "true":
                problems.append("FileProvider must be exported=false with grantUriPermissions=true")

    if attr(app, "allowBackup") == "true":
        for rules, tag in (("backup_rules.xml", "full-backup-content"), ("data_extraction_rules.xml", "cloud-backup")):
            tree = ET.parse(f"{backup_dir}/{rules}").getroot()
            scope = tree if tag == "full-backup-content" else tree.find("cloud-backup")
            excluded = {(e.attrib.get("domain"), e.attrib.get("path")) for e in scope.findall("exclude")}
            for domain in ("file", "database", "sharedpref"):
                if (domain, ".") not in excluded:
                    problems.append(f"{rules}: {domain} is not excluded from cloud backup")

    for p in problems:
        print(p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
