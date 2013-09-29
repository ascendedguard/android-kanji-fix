## Android Kanji Fix

An application to fix Japanese glyph rendering in Android 4.1 and up. [This thread on XDA](http://forum.xda-developers.com/showthread.php?t=1901424) describes the issue and possible fixes. **Root access is required!**

**Use at your own risk!** This modifies system files and I can't guarantee it works on all devices. I recommend making a backup if you're concerned about data loss.

In short, Android will render Chinese glyphs by default when the system language is set to anything but Japanese. By changing the default ordering of fonts, this app indicates Android should prioritize Japanese Kanji.

All superuser commands are using [Chainfire's libsuperuser](https://github.com/Chainfire/libsuperuser) classes.

More information about what this application does can be found in the wiki.