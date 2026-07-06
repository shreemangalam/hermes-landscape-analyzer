# Hermes demo — walks through the full incident scenario against a running instance.
# Start the app first:  .\mvnw.cmd spring-boot:run

$base = "http://localhost:8080"

function Show($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

Show "Landscape statistics"
curl.exe -s "$base/landscape/statistics"

Show "Landscape warnings (cycle + single-source consumers)"
curl.exe -s "$base/landscape/warnings"

Show "Impact analysis: SAP_ECC complete outage"
curl.exe -s -X POST "$base/analysis/impact" -H "Content-Type: application/json" `
    -d '{\"system\":\"SAP_ECC\",\"degradationType\":\"COMPLETE_OUTAGE\"}'

Show "Recovery sequence: LEGACY_WMS (sits on the landscape cycle)"
curl.exe -s -X POST "$base/analysis/recovery-sequence" -H "Content-Type: application/json" `
    -d '{\"system\":\"LEGACY_WMS\"}'

Show "Health map after the incident"
curl.exe -s "$base/landscape/health-map"

Show "Reset landscape to healthy"
curl.exe -s -X POST "$base/landscape/reset"
