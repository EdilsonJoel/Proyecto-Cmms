# Genera los recursos visuales de ManteniMax:
#   logo.png       - logotipo (engranaje Feather sobre degradado azul)
#   icono.ico      - icono multi-tamano para el ejecutable y el acceso directo
#   logo-*.png     - versiones 256/128/64/32/16
# Uso: powershell -ExecutionPolicy Bypass -File build\generar-iconos.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent $PSScriptRoot
$fuente = Join-Path $raiz 'build\feather.ttf'
$img = Join-Path $raiz 'src\main\resources\img'
New-Item -ItemType Directory -Path $img -Force | Out-Null

$GLIFO = [string][char]57555   # Feather SETTINGS (engranaje)

function New-Logo([int]$size, [string]$ruta) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.PixelOffsetMode = 'HighQuality'

    $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $c1 = [System.Drawing.Color]::FromArgb(255, 37, 99, 235)    # azul Primer
    $c2 = [System.Drawing.Color]::FromArgb(255, 56, 189, 248)   # cielo
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 48)

    # rectangulo redondeado (radio ~22%)
    $r = [int]($size * 0.225)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
    $path.AddArc($size - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
    $path.AddArc($size - 2 * $r, $size - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $path.AddArc(0, $size - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $path.CloseFigure()
    $g.FillPath($brush, $path)

    # brillo sutil arriba
    $brillo = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle(0, 0, $size, [int]($size * 0.55))),
        [System.Drawing.Color]::FromArgb(70, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(0, 255, 255, 255), 90)
    $g.FillPath($brillo, $path)

    # engranaje blanco (mismo glifo que los iconos de la interfaz)
    $pfc = New-Object System.Drawing.Text.PrivateFontCollection
    $pfc.AddFontFile($fuente)
    $tamanho = [int]($size * 0.60)
    $font = New-Object System.Drawing.Font($pfc.Families[0], $tamanho,
        [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $rectF = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    $g.DrawString($GLIFO, $font, [System.Drawing.Brushes]::White, $rectF, $sf)

    $g.Dispose()
    $bmp.Save($ruta, [System.Drawing.Imaging.ImageFormat]::Png)
    return $bmp
}

# --- logotipos en varias escalas
$base = New-Logo 512 (Join-Path $img 'logo.png')
foreach ($s in 256, 128, 64, 32, 16) {
    $thumb = New-Object System.Drawing.Bitmap($s, $s)
    $g = [System.Drawing.Graphics]::FromImage($thumb)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.DrawImage($base, 0, 0, $s, $s)
    $g.Dispose()
    $thumb.Save((Join-Path $img "logo-$s.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $thumb.Dispose()
}

# --- icono .ico (contenedor PNG multipagina, formato Vista+)
$entradas = @(
    @{ t = 16; archivo = 'logo-16.png' },
    @{ t = 32; archivo = 'logo-32.png' },
    @{ t = 48; archivo = 'logo-64.png' },
    @{ t = 64; archivo = 'logo-64.png' },
    @{ t = 128; archivo = 'logo-128.png' },
    @{ t = 256; archivo = 'logo-256.png' }
)
$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ms)
$bw.Write([uint16]0)              # reserved
$bw.Write([uint16]1)              # type: icon
$bw.Write([uint16]$entradas.Count)
$blobOffset = 6 + 16 * $entradas.Count
$blobs = @()
foreach ($e in $entradas) {
    $ruta = Join-Path -ChildPath $e.archivo -Path $img
    $bytes = [System.IO.File]::ReadAllBytes($ruta)
    $blobs += ,@([int]$e.t, $bytes)
}
foreach ($b in $blobs) {
    $t = $b[0]; $bytes = $b[1]
    $dim = if ($t -ge 256) { 0 } else { $t }
    $bw.Write([byte]$dim)         # ancho
    $bw.Write([byte]$dim)         # alto
    $bw.Write([byte]0)            # paleta
    $bw.Write([byte]0)            # reservado
    $bw.Write([uint16]1)          # planos
    $bw.Write([uint16]32)         # bits
    $bw.Write([uint32]$bytes.Length)
    $bw.Write([uint32]$blobOffset)
    $blobOffset += $bytes.Length
}
foreach ($b in $blobs) { $bw.Write($b[1]) }
$icoPath = Join-Path $raiz 'build\icono.ico'
[System.IO.File]::WriteAllBytes($icoPath, $ms.ToArray())
$bw.Dispose(); $ms.Dispose(); $base.Dispose()

Write-Host "OK: logo.png, logo-{256,128,64,32,16}.png en $img"
Write-Host "OK: $icoPath ($([System.IO.File]::ReadAllBytes($icoPath).Length) bytes)"
