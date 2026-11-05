#!/usr/bin/env python3
"""Static palette/content-color regression checks; not device rendering or accessibility instrumentation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
THEME = (ROOT / 'app/src/main/java/com/adnan/memora/ui/theme/Theme.kt').read_text()
APP = (ROOT / 'app/src/main/java/com/adnan/memora/ui/MemoraApp.kt').read_text()


def palette(name):
    body = THEME.split(f'private val {name} = ', 1)[1].split('\n)', 1)[0]
    values = dict((k, int(v, 16)) for k, v in re.findall(r'(\w+)\s*=\s*Color\(0xFF([A-Fa-f0-9]{6})\)', body))
    values.update({k: 0xFFFFFF for k in re.findall(r'(\w+)\s*=\s*Color.White', body)})
    return values


def luminance(rgb):
    channels = [(rgb >> shift & 255) / 255 for shift in (16, 8, 0)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(v * w for v, w in zip(linear, (.2126, .7152, .0722)))


def ratio(a, b):
    x, y = sorted((luminance(a), luminance(b)))
    return (y + .05) / (x + .05)


class DarkContrastContracts(unittest.TestCase):
    def test_theme_sets_default_content_color_inside_material_theme(self):
        body = THEME.split('fun MemoraTheme(', 1)[1]
        self.assertLess(body.index('MaterialTheme(colorScheme'), body.index('LocalContentColor provides MaterialTheme.colorScheme.onBackground'))
        self.assertIn('content()', body)

    def test_transparent_scaffold_explicitly_inherits_readable_text(self):
        self.assertRegex(APP, r'Scaffold\(\s*containerColor = Color.Transparent,\s*contentColor = MaterialTheme.colorScheme.onBackground,')

    def test_night_general_text_is_light(self):
        colors = palette('Night')
        for name in ('onBackground', 'onSurface', 'onSurfaceVariant'):
            with self.subTest(role=name):
                self.assertGreater(luminance(colors[name]), .60)

    def test_day_general_text_remains_dark(self):
        colors = palette('Day')
        for name in ('onBackground', 'onSurface', 'onSurfaceVariant'):
            with self.subTest(role=name):
                self.assertLess(luminance(colors[name]), .15)

    def test_body_and_action_palette_pairs_meet_4_5_to_1(self):
        pairs = [('onBackground', 'background'), ('onSurface', 'surface'),
                 ('onSurfaceVariant', 'surfaceVariant'), ('onPrimary', 'primary'),
                 ('onPrimaryContainer', 'primaryContainer'), ('onSecondary', 'secondary'),
                 ('onSecondaryContainer', 'secondaryContainer'), ('onTertiary', 'tertiary'),
                 ('onErrorContainer', 'errorContainer')]
        surfaces = ('surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer',
                    'surfaceContainerHigh', 'surfaceContainerHighest')
        pairs += [(fg, bg) for fg in ('onSurface', 'onSurfaceVariant') for bg in surfaces]
        for name in ('Day', 'Night'):
            colors = palette(name)
            for fg, bg in pairs:
                with self.subTest(theme=name, foreground=fg, background=bg):
                    self.assertGreaterEqual(ratio(colors[fg], colors[bg]), 4.5)

    def test_counter_gold_blue_labels_remain_readable(self):
        panel=(ROOT/'app/src/main/java/com/adnan/memora/ui/screens/CoveragePanel.kt').read_text()
        colors=re.findall(r'val (photoTint|videoTint) = if \(dark\) Color\(0xFF([0-9A-F]+)\) else Color\(0xFF([0-9A-F]+)\)',panel)
        self.assertEqual(len(colors),2)
        for role,night,day in colors:
            for theme,value in [('Night',night),('Day',day)]:
                with self.subTest(role=role,theme=theme):
                    self.assertGreaterEqual(ratio(int(value,16),palette(theme)['surfaceContainer']),4.5)


if __name__ == '__main__':
    unittest.main(verbosity=2)
