# Модели в Blender

Все модели строятся скриптами в живом Blender через сервер аддона [BlenderMCP](https://github.com/ahujasid/blender-mcp)
(`localhost:9876`). Исходники в `blender/*.blend`.

| Скрипт | Модель | Текстура |
|---|---|---|
| `buk.py` | `buk.obj` | `textures/block/buk.png` (512) |
| `tor.py` | `tor.obj` | `textures/block/tor.png` (512) |
| `pantsir.py` | `pantsir.obj` | `textures/block/pantsir.png` (512) |
| `nebo.py` | `radar_nebo.obj` | `textures/block/radar_nebo.png` (512) |
| `defense_assembler.py` | `defense_assembler.obj` (цех, кран, руки) | `textures/block/defense_assembler.png` (1024) |

`helpers.py` — материалы (камуфляж, бетон, сталь в sRGB-цветах Бука/Тора) и примитивы. `bake.py` — UV-развёртка,
запекание цвета в атлас, экспорт OBJ и список острых рёбер для контуров.

Пересборка, например Бука (из корня репозитория, Blender с запущенным сервером BlenderMCP):

```bash
cd tools/blender
{ cat helpers.py buk.py bake.py; echo "bake('buk', 512, 'textures/block/buk.png', 'models/obj/buk.obj', 'blender/buk_m1.blend')"; } > /tmp/run.py
python bl.py code /tmp/run.py
cd ../..
python tools/outline.py src/main/resources/assets/ntm_air_defense/textures/block/buk.png build/buk_outlines.json
python tools/obj2mesh.py src/main/resources/assets/ntm_air_defense/models/obj/buk.obj
```

`bl.py shot out.png` снимает вьюпорт. Оси вращения частей — в `docs/animation.md`.
