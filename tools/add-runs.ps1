$f = 'F:\TCM\mods\ctdhost\build.gradle'
$t = [IO.File]::ReadAllText($f, [Text.Encoding]::UTF8)
if ($t -notmatch 'runs \{') {
    $block = @'
neoForge {
    version = project.neo_version

    // ModDevGradle 不会自动创建 runClient/runServer，必须显式声明
    runs {
        client {
            client()
        }
        server {
            server()
        }
    }
'@
    $t = $t.Replace("neoForge {`r`n    version = project.neo_version", $block)
    if ($t -notmatch 'runs \{') {
        $t = $t.Replace("neoForge {`n    version = project.neo_version", $block)
    }
    [IO.File]::WriteAllText($f, $t, (New-Object Text.UTF8Encoding($false)))
}
Select-String -Path $f -Pattern 'neoForge|runs \{|server\(\)|client\(\)|version = project.neo' | ForEach-Object { "{0}: {1}" -f $_.LineNumber, $_.Line.Trim() }
