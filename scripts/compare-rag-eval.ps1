param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$BaseRunId,
    [string]$TargetRunId,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken
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

if (-not $BaseRunId -or -not $TargetRunId) {
    throw "Provide both -BaseRunId and -TargetRunId."
}

$token = Get-AccessToken
$headers = @{
    Authorization = "Bearer $token"
}

$uri = "$BaseUrl/api/rag/evals/compare?baseRunId=$BaseRunId&targetRunId=$TargetRunId"
$report = Invoke-RestMethod -Method Get -Uri $uri -Headers $headers

Write-Host "BaseRun:" $report.baseRun.runId
Write-Host "TargetRun:" $report.targetRun.runId
Write-Host "PassRateDelta:" "$($report.delta.passRateDelta)%"
Write-Host "AnswerAccuracyDelta:" "$($report.delta.answerAccuracyDelta)%"
Write-Host "CitationHitRateDelta:" "$($report.delta.citationHitRateDelta)%"
Write-Host "RefusalAccuracyDelta:" "$($report.delta.refusalAccuracyDelta)%"
Write-Host "ChangedCases:"
$report.caseDiffs |
    Where-Object { $_.change -ne "unchanged" } |
    Select-Object caseId, change, baseFailureReason, targetFailureReason, targetTraceId |
    Format-Table -AutoSize
