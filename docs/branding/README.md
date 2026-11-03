# Pololei branding

Logo, icons and favicons of pololei, derived from [the original concept](pololei-logo-concept-v1.png) and using the same asset types and colors as Paniolo, another NaluKit project.

| File | Use |
| --- | --- |
| `pololei-banner.svg` / `.png` | Horizontal logo for light backgrounds, e.g. README |
| `pololei-banner-dark.svg` / `.png` | Horizontal logo for dark backgrounds |
| `pololei-logo.svg` / `.png` | Stacked logo, turtle above wordmark, for light backgrounds |
| `pololei-logo-dark.svg` / `.png` | Stacked logo for dark backgrounds |
| `pololei-icon.svg` / `.png` | Turtle only, square canvas, for light backgrounds |
| `pololei-icon-dark.svg` / `.png` | Turtle only for dark backgrounds |
| `pololei-mark.svg` / `.png` | Small mark: the wordmark's p with a teal dot |
| `pololei-mark-dark.svg` / `.png` | Small mark for dark backgrounds |
| `pololei-favicon.svg` | Small pale mark and teal dot on a rounded navy tile |
| `pololei-favicon-32.png` | Browser favicon, 32 × 32 px |
| `pololei-favicon-180.png` | Apple touch icon, 180 × 180 px |
| `pololei-favicon-512.png` | Web app manifest or avatar, 512 × 512 px |
| `favicon.ico` | Browser favicon with 16, 32 and 48 px images |
| `pololei-logo-concept-v1.png` | Unmodified original AI-generated concept, 1254 × 1254 px, white background |

The logo, banner, icon and mark have transparent backgrounds, including the dark variants. Dark variants replace navy artwork with pale blue; they do not add a dark background. The favicon has a navy tile, with transparency outside its rounded corners.

Use the mark or favicon at 16–32 px. The full turtle is intended for larger sizes.

## Colors

- Navy: `#01244F`
- Teal: `#019ABC`
- Pale blue for dark backgrounds: `#E2ECF5`

## Formats and sizes

SVGs contain real vector paths, with no embedded raster images, fonts or external dependencies. Prefer SVG for scalable display. PNGs are rendered from the corresponding SVGs. Banner, logo, icon and mark are 8-bit palette images (up to 256 colors) with transparency; the favicon PNGs are 32-bit RGBA.

PNG sizes: banner 900 × 244 px, stacked logo 640 × 574 px, icon 256 × 256 px and mark 512 × 512 px. Banner and stacked logo preserve the aspect ratios of their SVGs.

The concept's white background and subtle shading were removed during tracing. The turtle and wordmark were separated, normalized to the two brand colors and traced using VTracer. The wordmark is traced from the concept, not set in a font. The small mark uses the traced p and the dot of the wordmark's i. The concept itself remains unchanged.

## README usage

The project README uses the banner with a dark variant; paths are relative to the repository root:

```html
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/branding/pololei-banner-dark.svg">
  <img src="docs/branding/pololei-banner.svg" alt="Pololei" width="480">
</picture>
```

## Favicons

When these files are served from the same directory as the page:

```html
<link rel="icon" href="pololei-favicon.svg" type="image/svg+xml">
<link rel="icon" href="favicon.ico" sizes="16x16 32x32 48x48">
<link rel="apple-touch-icon" href="pololei-favicon-180.png">
```
