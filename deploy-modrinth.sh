#!/usr/bin/env sh
set -eu

PROFILE_NAME=${PROFILE_NAME:-Waylandcraft-optimized}
APP_DB=${APP_DB:-"$HOME/.local/share/ModrinthApp/app.db"}
PROFILES_DIR=${PROFILES_DIR:-"$HOME/.local/share/ModrinthApp/profiles"}
BACKUP_DIR=${BACKUP_DIR:-"$HOME/.local/share/ModrinthApp/waylandcraft-jar-backups"}

cd "$(dirname "$0")"

echo "Building native library and mod jar..."
./build.sh "$@"

profile_path=$(sqlite3 -noheader "$APP_DB" "select path from profiles where name='$PROFILE_NAME' limit 1;")
if [ -z "$profile_path" ]; then
	echo "Could not find Modrinth profile named '$PROFILE_NAME' in $APP_DB" >&2
	exit 1
fi

target_dir="$PROFILES_DIR/$profile_path/mods"
target_jar="$target_dir/waylandcraft-1.0.0.jar"
built_jar="build/libs/waylandcraft-1.0.0.jar"

if [ ! -f "$built_jar" ]; then
	echo "Built jar not found: $built_jar" >&2
	exit 1
fi

mkdir -p "$target_dir" "$BACKUP_DIR"

if [ -f "$target_jar" ]; then
	stamp=$(date +%Y%m%d-%H%M%S)
	backup="$BACKUP_DIR/waylandcraft-1.0.0-$stamp.jar"
	cp -a "$target_jar" "$backup"
	echo "Backed up old jar to $backup"
fi

cp -a "$built_jar" "$target_jar"

echo "Installed $built_jar"
echo "      to $target_jar"
