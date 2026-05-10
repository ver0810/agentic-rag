param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Dataset = "sample-template",
    [string]$KbId,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [int]$TopK = 5,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

function Get-AccessToken {
    if ($AccessToken) {
        return $AccessToken
    }
    if (-not $Username -or -not $Password) {
        throw "Provide -AccessToken or both -Username and -Password."
    }
    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/user/login" -ContentType "application/json" -Body $loginBody
    if (-not $loginResponse.accessToken) {
        throw "Login succeeded but accessToken was empty."
    }
    return $loginResponse.accessToken
}

$token = Get-AccessToken

$headers = @{
    Authorization = "Bearer $token"
}

$body = @{
    dataset = $Dataset
    kbIdOverride = $KbId
    topKOverride = $TopK
} | ConvertTo-Json

$report = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/rag/evals/run" -Headers $headers -ContentType "application/json" -Body $body

if (-not $OutFile) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutDir = Join-Path $PSScriptRoot "..\\artifacts\\rag-eval"
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $OutFile = Join-Path $OutDir "$Dataset-$timestamp.json"
}

$report | ConvertTo-Json -Depth 8 | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Dataset:" $report.dataset
Write-Host "RunId:" $report.runId
Write-Host "Passed:" "$($report.summary.passed)/$($report.summary.total)"
Write-Host "PassRate:" "$($report.summary.passRate)%"
Write-Host "AnswerAccuracy:" "$($report.summary.answerAccuracy)%"
Write-Host "CitationHitRate:" "$($report.summary.citationHitRate)%"
Write-Host "RefusalAccuracy:" "$($report.summary.refusalAccuracy)%"
Write-Host "Report:" $OutFile

if ($report.summary.failed -gt 0) {
    Write-Error "RAG evaluation failed with $($report.summary.failed) failing cases."
}
