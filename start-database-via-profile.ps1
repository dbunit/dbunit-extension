# run the IT setup for the specified profile

$profile = $args[0]

if($Null -eq $profile)
{
    Write-Error -Message "Error: must specify Maven database profile"
    get-content database-profiles.txt
    return
}

Write-Output "-------------------------------------"
Write-Output "Running IT setup for $profile"
Write-Output "-------------------------------------"

.\mvnw pre-integration-test "-P$($profile)"
