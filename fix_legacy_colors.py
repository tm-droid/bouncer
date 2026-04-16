#!/usr/bin/env python3
"""
Fix legacy color tokens in Bouncer Android premium components.
Replaces WicketRed, SuccessGreen, WarningYellow, ChartBlue, LiveBlue, 
ShinyGoldMid, ShinyGoldLight, ShinyGoldDeep, BoundaryFour, BoundarySix
with BouncerDesignSystem.colors.* equivalents.
"""

import re
import os
import sys
from pathlib import Path

TOKEN_MAP = {
    'WicketRed': 'colors.statusLive',
    'SuccessGreen': 'colors.statusSuccess',
    'WarningYellow': 'colors.statusWarning',
    'BoundarySix': 'colors.statusWarning',
    'BoundaryFour': 'colors.statusWarning',
    'ChartBlue': 'colors.statusInfo',
    'LiveBlue': 'colors.statusInfo',
    'ShinyGoldDeep': 'colors.goldPrimary',
    'ShinyGoldMid': 'colors.goldDark',
    'ShinyGoldLight': 'colors.goldLight',
}

DESIGN_SYSTEM_IMPORT = 'import com.tanush.bouncer.ui.design.BouncerDesignSystem'
VAL_COLORS_LINE = 'val colors = BouncerDesignSystem.colors'


def has_legacy_tokens(line: str) -> bool:
    """Check if a non-import, non-comment line has legacy tokens."""
    stripped = line.strip()
    if stripped.startswith('import ') or stripped.startswith('//') or stripped.startswith('*'):
        return False
    for token in TOKEN_MAP:
        if re.search(r'\b' + token + r'\b', line):
            return True
    return False


def replace_tokens_in_line(line: str) -> str:
    """Replace legacy tokens in a line (skip import/comment lines)."""
    stripped = line.strip()
    if stripped.startswith('import ') or stripped.startswith('//') or stripped.startswith('*'):
        return line
    
    result = line
    for token, replacement in TOKEN_MAP.items():
        result = re.sub(r'\b' + token + r'\b', replacement, result)
    return result


def process_file(filepath: Path) -> tuple[int, int]:
    """
    Process a single .kt file.
    Returns (tokens_replaced, composables_updated) counts.
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    
    # Count replaced tokens
    tokens_replaced = 0
    
    # --- Phase 1: Replace legacy tokens (skip import/comment lines) ---
    new_lines = []
    for line in lines:
        new_line = replace_tokens_in_line(line)
        if new_line != line:
            tokens_replaced += 1
        new_lines.append(new_line)
    
    # --- Phase 2: Add `val colors = BouncerDesignSystem.colors` to composables ---
    composables_updated = 0
    result_lines = []
    i = 0
    
    while i < len(new_lines):
        line = new_lines[i]
        
        # Detect @Composable annotation  
        if re.search(r'@Composable\b', line.strip()):
            result_lines.append(line)
            
            # Find the function opening brace
            brace_insert_after = None
            indent = ''
            
            # Check same line for fun + {
            if 'fun ' in line and '{' in line:
                brace_insert_after = i
                # Extract indentation
                indent = re.match(r'^(\s*)', line).group(1) + '    '
            else:
                # Lookahead for function definition
                j = i + 1
                while j < len(new_lines) and j < i + 30:
                    peek = new_lines[j]
                    
                    if 'fun ' in peek and '{' in peek:
                        brace_insert_after = j
                        indent = re.match(r'^(\s*)', peek).group(1) + '    '
                        break
                    elif re.match(r'^\s*\)\s*\{', peek) or re.match(r'^\s*\{', peek):
                        if 'fun ' in new_lines[max(0, j-1)] or any('fun ' in new_lines[k] for k in range(max(0, j-10), j)):
                            brace_insert_after = j
                            indent = re.match(r'^(\s*)', peek).group(1) + '    '
                            break
                    elif re.search(r'^package |^import |^data class |^object |^interface |^abstract ', peek.strip()):
                        break  # hit a new top-level declaration, stop
                    j += 1
            
            if brace_insert_after is not None:
                # Collect lines up to and including brace_insert_after
                k = i + 1
                while k <= brace_insert_after:
                    result_lines.append(new_lines[k])
                    k += 1
                
                # Check if val colors already exists right after the brace
                already_has_colors = False
                check_start = brace_insert_after + 1
                for ci in range(check_start, min(check_start + 8, len(new_lines))):
                    if VAL_COLORS_LINE in new_lines[ci]:
                        already_has_colors = True
                        break
                
                if not already_has_colors:
                    result_lines.append(indent + VAL_COLORS_LINE)
                    composables_updated += 1
                
                i = brace_insert_after + 1
                continue
        
        result_lines.append(line)
        i += 1
    
    # --- Phase 3: Ensure BouncerDesignSystem import exists ---
    final_content = '\n'.join(result_lines)
    
    if composables_updated > 0 and DESIGN_SYSTEM_IMPORT not in final_content:
        # Add import after the last import line
        lines_final = final_content.split('\n')
        last_import_idx = -1
        for idx, ln in enumerate(lines_final):
            if ln.strip().startswith('import '):
                last_import_idx = idx
        
        if last_import_idx >= 0:
            lines_final.insert(last_import_idx + 1, DESIGN_SYSTEM_IMPORT)
            final_content = '\n'.join(lines_final)
    
    if final_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(final_content)
        return tokens_replaced, composables_updated
    
    return 0, 0


def main():
    premium_dir = Path('app/src/main/java/com/tanush/bouncer/ui/components/premium')
    
    total_tokens = 0
    total_composables = 0
    files_changed = 0
    
    kt_files = sorted(premium_dir.glob('*.kt'))
    print(f"Processing {len(kt_files)} files...")
    
    for filepath in kt_files:
        tokens, composables = process_file(filepath)
        if tokens > 0 or composables > 0:
            files_changed += 1
            total_tokens += tokens
            total_composables += composables
            print(f"  {filepath.name}: {tokens} tokens replaced, {composables} composables updated")
    
    print(f"\nDone! {files_changed} files changed, {total_tokens} tokens replaced, {total_composables} composables updated")


if __name__ == '__main__':
    main()
