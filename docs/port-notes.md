# Перенос в Minecraft: что выяснено (NTM 1.0.2, Fabric 26.2)

Компилируемся против `com.hbm:ntm-next-fabric:1.0.2+mc26.2` и `dev.engine_room:crankshaft-fabric` из https://repo.warfactory.co/releases. Исходники `ntm-next-master` СТАРЕЕ jar:
в 1.0.2 пакет `com.hbm.blockentity.*` вместо `tileentity`. Справочник — декомпиляция CFR
(`java -jar cfr.jar hbm.jar --outputdir src`, CFR 0.152 качается с Google-зеркала Maven).

## Готово
- Модели: `src/main/resources/assets/ntm_air_defense/models/obj/{buk,tor}.obj` + `.hbm_mesh`
  (`tools/obj2mesh.py` — компилятор в формат NTM, проверен чтением родного turret_himars.hbm_mesh).
- Текстуры: `textures/block/{buk,tor}.png`. Анимации: `docs/animation.md`
  (поправка: петли переднего ряда люков Тора в OBJ — (x, 3.03, ∓0.78)).
- Каркас Gradle: build.gradle.kts, settings, gradle.properties.

## API аддонов NTM
- Точка входа `"hbm:addon"` (интерфейс `com.hbm.registration.Addon#register`) вызывается ДО
  `NtmCapabilities.declareAll()` — блоки получат энергию/мультиблок/тикеры автоматически.
- `Reg.in("ntm_air_defense")` → `.blockItem(name, factory, props)`, `.be(name, () -> new BlockEntityType<>(Ctor::new, Set.of(block)))
  .ticks(BE::tickClient, BE::tickServer)`, `.item(...)`, `.entity(name, () -> EntityType.Builder...build(key))`.
- Рендеры (клиент): `BlockEntityRendererRegistry.register`, `EntityRendererRegistry.register` (Fabric API).

## Классы-образцы
- Бук: блок `extends com.hbm.blocks.turret.TurretBaseArtillery` (как TurretHIMARS), BE
  `extends com.hbm.blockentity.turret.BlockEntityTurretBaseArtillery` (уже IRadarCommandReceiver).
- Тор: блок `extends TurretBaseNT` (как TurretChekhov, + `passiveEverywhere(core, facing, visitor, 5)`).
- `caps() = MachineCaps.of(BE_HANDLE).powerIn().itemsAtCells()`.
- GUI: `createMenu` → `new MenuTurretBase(id, inv, this)`; экран NTM сам возьмёт фон "base".
- Рендер: `extends com.hbm.client.render.RenderTurretBase<T>`, метод `emit(T, State, TurretFrame)`;
  части `frame.push().obj(mesh, mesh.partId("Name")).texture(tex)`, поворот `rotateY((state.yaw-180)°)`.
  Меши: `com.hbm.client.model.Meshes.load(Identifier)` (читает .hbm_mesh из ресурсов).
- Основание и иконка предмета — JSON `"fabric:type": "hbm:obj"` (образцы: turret_himars_*.json,
  turret_chekhov_*.json; смещения north (0.5,0.5), east (−0.5,0.5), south (−0.5,−0.5), west (0.5,−0.5)).

## Синхронизация (важно!)
NTM переписывает присваивания полей `@SyncField` плагином javac. У нас плагина нет:
- для полей NTM вызывать сеттеры (`setPower`, `turnTowardsAngle`, `hbm$syncSet$BlockEntityTurretBaseNT$tPos(...)`);
- для своих полей: `syncUnitsChanged(3, 1L << unit)` вручную, units 11..14,
  `syncUnitMask() = super | 0x7800L`, свои `writeSyncUnit/readSyncUnit` (как в HIMARS).

## Прочее
- Радар: `com.hbm.blockentity.machine.BlockEntityMachineRadar` — `entries` (RadarEntry.entityID), `getRange()`.
- Линкер: `com.hbm.items.tool.ItemRadarLinker`, компонент `ModDataComponents.RADAR_LINKER_TARGET` (Long).
  План: mixin в `useOn` — клик по радару запоминает радар, Shift+клик по пусковой привязывает её.
- Ракеты-цели: `com.hbm.entity.missile.EntityMissileBaseNT` (health 50, `hurtServer` → killMissile),
  исключить `EntityMissileAntiBallistic`. Взрыв: `ExplosionLarge.explode(level,x,y,z,str,cloud,rubble,shrapnel)`.
- Эффекты: `com.hbm.client.ClientEffects.spawnContrail(level,x,y,z,Contrail.HYDROGEN)`, `spawnRocketFlame(...)`.
- Энергия из батареи: `ItemEnergyTransfer.extract(this, SLOT_BATTERY, max - power, false)`.

## Статус
Написано (ещё НИ РАЗУ не компилировалось): AirDefense (Addon), TurretBuk/TurretTor, BlockEntitySAMBase
+ Buk/Tor, Geo, EntitySAM, SamTrail, client/RenderBuk/RenderTor/RenderSAM/AirDefenseClient,
mixin/RadarLinkerMixin; ресурсы: blockstates, модели блоков/предметов (hbm:obj), items, lang en/ru,
лут, рецепты сборочной машины. Сборка: `build.ps1` (копирует NTM + Crankshaft в libs, собирает,
ставит в инстанс). Следующий шаг — первая компиляция и правка ошибок API, затем тест в игре
(смещения/масштабы предметов в models/item подобраны на глаз).

## Сборочный цех (defense_assembler)
Блок `MachineDefenseAssembler` (9×9×7) наследует сборочную машину NTM; BE `BlockEntityDefenseAssembler`.
Тип BE и набор рецептов подменяются в конструкторе NTM mixin'ом `AssemblyMachineMixin` (BlockEntity
проверяет тип по блоку уже в super(), поэтому только так). Экран выбора рецептов — `AssemblerScreenMixin`.
Свой тип рецепта `ntm_air_defense:defense_assembler` (`DefenseRecipe`, `DefenseAssemblerRecipes`),
регистрируется и синхронизируется на клиент через `ModRecipes.addon(...)`; JSON как у сборочной машины.
Рецепты пусковых: data/ntm_air_defense/recipe/defense_assembler/. Ракеты остаются в сборочной машине NTM.
