# Исказитель 1.4.0 — Minecraft 1.12.2 Forge

**Обязателен** Scape and Run: Parasites (`srparasites`).  
Без SRP мод **не загрузится**.

**Cotesia Glomerata** (`srpcotesia`) — опциональная интеграция: Vagrant-игроки считаются своими, Исказитель их не атакует.

## Зависимости

| Мод | Роль |
|-----|------|
| Forge 1.12.2 | обязательно |
| **Scape and Run: Parasites** | **обязательно** (`required-after:srparasites`) |
| Cotesia Glomerata | опционально (`after:srpcotesia`) |

## Формы

- **Исказитель** — primitive, фаза 4, из Moving Flesh (50% HP)
- **Адаптированный Исказитель** — после 30 убийств / природный спавн с фазы 6

## Сборка

Java 8: `./gradlew clean build --no-daemon`  
JAR: `build/libs/parasitemimic-1.4.0.jar`

В `mods` должны лежать SRP и этот JAR. Cotesia — по желанию.
