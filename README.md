# Yet Another Minecraft Image Placing Addon
[![Latest Version](https://img.shields.io/github/v/release/josemmo/yamipa)](https://github.com/josemmo/yamipa/releases/latest)
![Minecraft Version](https://img.shields.io/badge/minecraft-%3E%3D1.16-blueviolet)
[![License](https://img.shields.io/github/license/josemmo/yamipa)](LICENSE)

Yamipa is an Spigot plugin that allows you to place images on any surface in your Minecraft server.
It is designed with performance and compatibility in mind, so even the most low-specs servers should be able to run it.

## Requirements
- Minecraft >=1.16 (tested on 1.16.4)
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)

## Installation
Download the JAR file for the [latest release](https://github.com/josemmo/yamipa/releases/latest) and copy it to the
"plugins" directory of your Bukkit/Spigot/Paper server. That's it!

## Configuration
Yamipa is ready to go out of the box. By default, it creates the following files and directories under the
`plugins/YamipaPlugin` directory:

- `cache`: A directory containing cached images to speed up the rendering process. You shouldn't modify its contents.
- `images`: **This is the directory where you put the image files** you want to place in your Minecraft world.
- `images.dat`: A file holding the list and properties (e.g. coordinates) of all placed images in your server. You
shouldn't modify its contents.

You can change the default path of these files by creating a `config.yml` file in the plugin configuration directory:
```yaml
verbose: false         # Set to "true" to enable more verbose logging
images-path: images    # Path to images directory
cache-path: cache      # Path to cache directory
data-path: images.dat  # Path to placed images database file
```

## Usage
This plugin adds the following commands:

- `/image list [<page>]`: List all available files in the images directory.
- `/image download <url> <filename>`: Download an image from a URL and place it in the images directory.
- `/image place <filename> <width> [<height>]`: Place an image of size `width`x`height` blocks.
- `/image remove`: Remove a placed image from the world without deleting the image file.
- `/image remove <radius>`: Remove all placed images in a radius of `radius` blocks around the player.

### Permissions
By default, only server OPs have all plugin permissions granted:

- `yamipa.list`
- `yamipa.download`
- `yamipa.place`
- `yamipa.remove`
- `yamipa.remove.radius`

## License
Yamipa is licensed under the [MIT License](LICENSE) and includes third-party software licensed under
the MIT License and the [GPLv3 License](https://www.gnu.org/licenses/gpl-3.0.html).
