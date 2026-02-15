use std::path::PathBuf;
use freedesktop_desktop_entry::{
    unicase::Ascii,
    DesktopEntry,
    get_languages_from_env, desktop_entries, find_app_by_id,
};
use cosmic_freedesktop_icons::lookup;

pub struct XDGSpecHelper {
    locales: Vec<String>,
    entries: Vec<DesktopEntry>,
}

impl XDGSpecHelper {
    pub fn init() -> Self {
        let locales = get_languages_from_env();
        let entries = desktop_entries(&locales);
        
        XDGSpecHelper {
            locales,
            entries
        }
    }

    fn entry(&self, app_id: &str) -> Option<&DesktopEntry> {
        find_app_by_id(&self.entries, Ascii::new(app_id))
    }

    pub fn resolve_name(&self, app_id: &str) -> Option<String> {
        let entry = self.entry(app_id)?;

        entry.name(&self.locales).map(|n| String::from(n))
    }

    pub fn resolve_icon_path(&self, app_id: &str) -> Option<PathBuf> {
        let entry = self.entry(app_id)?;
        let icon = entry.icon()?;

        // Absolute icon path
        let abspath = PathBuf::from(icon);
        if abspath.is_absolute() && abspath.is_file() {
            return Some(abspath);
        }

        // Lookup 64x64 icons
        let path = lookup(icon)
            .with_size(64)
            .with_scale(1)
            .find();

        // Fallback to any icon paths
        path.or_else(|| lookup(icon).find())
    }
}
