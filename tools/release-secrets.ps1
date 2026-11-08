# One-time release signing setup: creates the signing keystore (or reuses an existing one) and stores the
# 4 GitHub secrets used by .github/workflows/release.yml. Run it yourself, signed in with `gh auth login`.
# The password is typed here, kept only in memory, never shown nor written to disk; the keystore stays
# outside the repo.
# Usage: powershell -ExecutionPolicy Bypass -File tools/release-secrets.ps1
param(
    [string]$Keystore = "$HOME\orangetv-release.jks",
    [string]$Alias = "orangetv",
    [string]$Repo = "tsnaketech/orange-tv"
)
$ErrorActionPreference = "Stop"
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"

# Checked first, so nothing is created when the secrets cannot be stored.
gh auth status --hostname github.com *> $null
if ($LASTEXITCODE -ne 0) { throw "gh is not signed in: run 'gh auth login' first, then this script again." }

$reuse = Test-Path $Keystore
$plain = { param($s) [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)) }
if ($reuse) {
    Write-Host "Reusing the existing keystore $Keystore."
    $pw = & $plain (Read-Host "Its password" -AsSecureString)
} else {
    $pw = & $plain (Read-Host "New keystore password (at least 6 characters)" -AsSecureString)
    if ($pw -ne (& $plain (Read-Host "Same password again" -AsSecureString))) { throw "The two passwords differ." }
    if ($pw.Length -lt 6) { throw "Password too short (keytool needs 6 characters)." }
}
# Windows PowerShell pipes text to programs as ASCII: an accented letter would reach GitHub altered.
if ($pw -match '[^\x20-\x7E]') { throw "Use only unaccented letters, digits and symbols." }

# Given through an environment variable, not the command line (visible in the process list).
$env:ORANGETV_KS_PW = $pw
try {
    if ($reuse) {
        # A wrong password or alias must fail here, not in the release workflow.
        & $keytool -list -keystore $Keystore -alias $Alias -storepass:env ORANGETV_KS_PW *> $null
        if ($LASTEXITCODE -ne 0) { throw "Wrong password, or no key '$Alias' in $Keystore." }
    } else {
        & $keytool -genkeypair -keystore $Keystore -alias $Alias -keyalg RSA -keysize 4096 -validity 10000 `
            -dname "CN=Orange TV" -storepass:env ORANGETV_KS_PW -keypass:env ORANGETV_KS_PW
        if ($LASTEXITCODE -ne 0) { throw "keytool failed." }
    }

    # Values go to gh through stdin, so they never appear on the command line.
    function Set-Secret($name, $value) {
        $value | gh secret set $name --repo $Repo
        if ($LASTEXITCODE -ne 0) { throw "gh secret set $name failed." }
    }
    Set-Secret KEYSTORE_BASE64 ([Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore)))
    Set-Secret KEYSTORE_PASSWORD $pw
    Set-Secret KEY_PASSWORD $pw  # PKCS12 keystore: the key shares the store password
    Set-Secret KEY_ALIAS $Alias
} finally {
    Remove-Item Env:ORANGETV_KS_PW -ErrorAction SilentlyContinue
    $pw = $null
}
gh secret list --repo $Repo
Write-Host "Done. Back up $Keystore and its password: without them no later version installs over this one."
