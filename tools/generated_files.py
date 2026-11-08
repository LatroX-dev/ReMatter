"""Generated files of one generator run: written, or with --check compared while every file stays untouched."""

import json

from material_tiles import png_bytes


class GeneratedFiles:
    def __init__(self, root, check):
        self.root = root
        self.check = check
        self.count = 0

    def json(self, path, value):
        self.bytes(path, (json.dumps(value, indent=2) + "\n").encode())

    def png(self, path, width, height, pixel):
        self.bytes(path, png_bytes(width, height, pixel))

    def bytes(self, path, content):
        self.count += 1
        if self.check:
            if not path.is_file() or path.read_bytes() != content:
                raise ValueError(f"Generated file differs: {path.relative_to(self.root)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
