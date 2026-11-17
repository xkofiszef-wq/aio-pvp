# AIO PvP - pomocnik odtwarzacza multimediów.
# Czyta aktualnie odtwarzany utwór z kontrolek multimediów Windows (Spotify, przeglądarka itp.)
# i wypisuje jedną linię JSON co ok. 0,5 s. Polecenia (play/pause/next/prev) czyta z pliku podanego w -CommandFile.
# Okładkę zapisuje jako PNG do -ArtFile, gdy zmieni się utwór.
param(
    [Parameter(Mandatory = $true)][string]$CommandFile,
    [Parameter(Mandatory = $true)][string]$ArtFile,
    [int]$ParentPid = 0
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($operation, [Type]$resultType) {
    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await ($managerType::RequestAsync()) $managerType

function Escape([string]$text) {
    if ($null -eq $text) { return '' }
    return ($text -replace '\\', '\\' -replace '"', '\"' -replace "`r", ' ' -replace "`n", ' ')
}

function Save-Art($properties) {
    try {
        if ($null -eq $properties.Thumbnail) { return $false }
        $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
        $stream = Await ($properties.Thumbnail.OpenReadAsync()) $streamType
        # Wywołanie przez refleksję: PowerShell 5.1 nie umie sam rzutować surowego obiektu WinRT na IInputStream.
        $asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream]))
        $netStream = $asStream.Invoke($null, @($stream))
        $image = [System.Drawing.Image]::FromStream($netStream)
        $size = 64
        $bitmap = New-Object System.Drawing.Bitmap $size, $size
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        # Środek okładki jako kwadrat (Spotify daje kwadrat, YouTube 16:9).
        $side = [Math]::Min($image.Width, $image.Height)
        $src = New-Object System.Drawing.Rectangle ([int](($image.Width - $side) / 2)), ([int](($image.Height - $side) / 2)), $side, $side
        $graphics.DrawImage($image, (New-Object System.Drawing.Rectangle 0, 0, $size, $size), $src, [System.Drawing.GraphicsUnit]::Pixel)
        $tmp = $ArtFile + '.tmp'
        $bitmap.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        Move-Item -Force $tmp $ArtFile
        $graphics.Dispose(); $bitmap.Dispose(); $image.Dispose(); $netStream.Dispose()
        return $true
    } catch {
        return $false
    }
}

$lastKey = ''
$artVersion = 0
while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit }
    try {
        $session = $manager.GetCurrentSession()
        if (Test-Path $CommandFile) {
            $command = (Get-Content $CommandFile -Raw).Trim()
            Remove-Item $CommandFile -Force
            if ($null -ne $session) {
                switch ($command) {
                    'toggle' { [void](Await ($session.TryTogglePlayPauseAsync()) ([bool])) }
                    'next' { [void](Await ($session.TrySkipNextAsync()) ([bool])) }
                    'prev' { [void](Await ($session.TrySkipPreviousAsync()) ([bool])) }
                }
            }
        }

        if ($null -eq $session) {
            [Console]::Out.WriteLine('{"active":false}')
        } else {
            $properties = Await ($session.TryGetMediaPropertiesAsync()) $propsType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $playing = $playback.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing
            $key = $properties.Title + '|' + $properties.Artist
            if ($key -ne $lastKey) {
                $lastKey = $key
                if (Save-Art $properties) { $artVersion++ } elseif (Test-Path $ArtFile) { Remove-Item $ArtFile -Force; $artVersion++ }
            }

            # Pozycja z kontrolek jest aktualizowana rzadko, więc dodajemy czas od ostatniej aktualizacji.
            $position = $timeline.Position.TotalSeconds
            if ($playing) { $position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalSeconds }
            $duration = ($timeline.EndTime - $timeline.StartTime).TotalSeconds
            $line = '{"active":true,"title":"' + (Escape $properties.Title) + '","artist":"' + (Escape $properties.Artist) +
                '","app":"' + (Escape $session.SourceAppUserModelId) + '","position":' + [Math]::Round($position, 1).ToString([Globalization.CultureInfo]::InvariantCulture) +
                ',"duration":' + [Math]::Round($duration, 1).ToString([Globalization.CultureInfo]::InvariantCulture) +
                ',"playing":' + $playing.ToString().ToLower() + ',"art":' + $artVersion + '}'
            [Console]::Out.WriteLine($line)
        }
    } catch {
        [Console]::Out.WriteLine('{"active":false,"error":"' + (Escape $_.Exception.Message) + '"}')
    }

    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 500
}
