#!/bin/bash

# Setup script for git hooks
# This script installs a pre-commit hook that keeps CLAUDE.md,
# AGENTS.md, GEMINI.md, and .junie/guidelines.md in sync.

set -e

echo "Setting up git hooks..."

# Check if we're in a git repository
if [ ! -d ".git" ]; then
    echo "Error: Not in a git repository"
    exit 1
fi

# Create .junie directory if it doesn't exist
mkdir -p .junie

# Create the pre-commit hook
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash

# Pre-commit hook to keep CLAUDE.md, AGENTS.md, GEMINI.md, and .junie/guidelines.md in sync

FILES=("CLAUDE.md" "AGENTS.md" "GEMINI.md" ".junie/guidelines.md")
changed=()
for f in "${FILES[@]}"; do
    if git diff --cached --quiet -- "$f" >/dev/null 2>&1; then
        :
    else
        changed+=("$f")
    fi
done

if [ "${#changed[@]}" -gt 1 ]; then
    echo "Error: Multiple guideline files changed (${changed[*]}). Commit aborted."
    exit 1
fi

if [ "${#changed[@]}" -eq 1 ]; then
    src="${changed[0]}"
    for f in "${FILES[@]}"; do
        [ "$f" = "$src" ] && continue
        if [ "$f" = ".junie/guidelines.md" ]; then
            mkdir -p .junie
        fi
        cp "$src" "$f"
        git add "$f"
    done
    echo "Synchronized guideline files using $src"
fi

exit 0
EOF

# Make the hook executable
chmod +x .git/hooks/pre-commit

echo "Pre-commit hook installed successfully!"
echo "The hook keeps CLAUDE.md, AGENTS.md, GEMINI.md, and .junie/guidelines.md in sync on each commit."

# Test the hook
echo "Testing the hook..."
.git/hooks/pre-commit

echo "Setup complete!"
