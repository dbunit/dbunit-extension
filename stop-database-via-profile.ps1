# stop the database for the specified profile

$profile = $args[0]

if($Null -eq $profile)
{
    Write-Error -Message "Error: must specify Maven database profile"
    get-content database-profiles.txt
    return
}

Write-Output "--------------------------------------------"
Write-Output "Stopping database setup for $profile"
Write-Output "--------------------------------------------"

.\mvnw io.fabric8:docker-maven-plugin:stop "-P$($profile)"
