<#
.SYNOPSIS
    Logs into the FX Monitor API and downloads the multi-sheet Excel portfolio
    report for Power BI. The export endpoint has no UI button and requires a
    Bearer JWT, so this handles login + download in one step.

.EXAMPLE
    .\export-fx-report.ps1
    .\export-fx-report.ps1 -Email demo-breach@fxmonitor.com -HomeCurrency EUR -Out C:\reports\breach.xlsx
#>
param(
    [string]$BaseUrl      = "http://localhost:8080",
    [string]$Email        = "demo@fxmonitor.com",
    [string]$Password     = "demo1234",
    [string]$HomeCurrency = "USD",   # NOTE: not -Home; $Home is a reserved PowerShell variable
    [string]$Out          = "fx-portfolio.xlsx"
)

$ErrorActionPreference = "Stop"

Write-Host "Logging in as $Email ..."
$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$token = $login.accessToken
if (-not $token) { throw "No accessToken in login response." }

Write-Host "Downloading Excel report (home=$HomeCurrency) ..."
Invoke-WebRequest -Uri "$BaseUrl/api/reports/export.xlsx?home=$HomeCurrency" `
    -Headers @{ Authorization = "Bearer $token" } `
    -OutFile $Out

Write-Host "Saved to $((Resolve-Path $Out).Path)"
