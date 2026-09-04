-- Generates packs/baseline/art/<name>.aseprite from the character sheets in the same directory
-- (<name>.png). One tagged .aseprite per character, ready for the buildAtlases pipeline.
--
-- A sheet is a grid of rows, one animation per row, in the order listed in CHARACTER_ROWS below
-- (the same order buildSrc/PlaceholderArt writes). Frame counts are detected per row by scanning
-- for the first fully-transparent cell, so rows may have different lengths (idle 4, walk 6, ...).
--
-- Tags are named <state>_<direction> for directions down/right/up. LEFT is intentionally NOT
-- authored: CharacterSpriteLoader derives it by flipping RIGHT. Only idle/walk are consumed by the
-- engine today; extra rows (attack, hurt, death, ...) are exported into the atlas unused.
--
-- Run (from the repo root):
--   ./gradlew genCharacterAseprites            (or: aseprite -b -script art/tools/gen_character_aseprites.lua)
--   ./gradlew buildAtlases -Ppack=baseline
-- then commit the .aseprite files and the atlas.

local ART = "packs/baseline/art/"

-- tag name -> row in the sheet. Extend for sheets with more rows (e.g. attack_down = 6 ...).
local CHARACTER_ROWS = {
  { name = "idle_down",  row = 0 },
  { name = "idle_right", row = 1 },
  { name = "idle_up",    row = 2 },
  { name = "walk_down",  row = 3 },
  { name = "walk_right", row = 4 },
  { name = "walk_up",    row = 5 },
}

-- Each entry: the sheet's frame size in px and its row layout. Add a line per character sheet.
local SHEETS = {
  { name = "player", grid = 48, rows = CHARACTER_ROWS },
  { name = "npc",    grid = 48, rows = CHARACTER_ROWS },
}

--- True if any pixel in the grid cell at (col,row) is non-transparent.
local function cellHasArt(img, col, row, grid)
  local ox, oy = col * grid, row * grid
  for y = 0, grid - 1 do
    for x = 0, grid - 1 do
      if app.pixelColor.rgbaA(img:getPixel(ox + x, oy + y)) > 0 then
        return true
      end
    end
  end
  return false
end

--- Number of leading non-empty cells in a row (the row's real frame count).
local function frameCount(img, row, grid, maxCols)
  local n = 0
  while n < maxCols and cellHasArt(img, n, row, grid) do
    n = n + 1
  end
  return n
end

local function build(sheet)
  local srcPath = ART .. sheet.name .. ".png"
  local outPath = ART .. sheet.name .. ".aseprite"
  local grid = sheet.grid

  local src = app.open(srcPath)
  if src == nil then error("could not open " .. srcPath) end
  if src.colorMode ~= ColorMode.RGB then
    app.activeSprite = src
    app.command.ChangePixelFormat{ format = "rgb" }
  end
  local srcImg = src.cels[1].image
  local maxCols = src.width // grid
  local maxRows = src.height // grid

  -- Measure every row first, so we know the total frame count before building the sprite.
  local plan, total = {}, 0
  for _, r in ipairs(sheet.rows) do
    if r.row >= maxRows then
      error(sheet.name .. ": layout names row " .. r.row .. " but the sheet has only " .. maxRows)
    end
    local n = frameCount(srcImg, r.row, grid, maxCols)
    if n == 0 then error(sheet.name .. ": row " .. r.row .. " (" .. r.name .. ") is empty") end
    table.insert(plan, { name = r.name, row = r.row, frames = n })
    total = total + n
  end

  local sprite = Sprite(grid, grid, ColorMode.RGB)
  local layer = sprite.layers[1]
  while #sprite.frames < total do sprite:newFrame() end

  -- Blit each cell into its own frame, in tag order.
  local f = 1
  for _, p in ipairs(plan) do
    for col = 0, p.frames - 1 do
      local cel = Image(grid, grid, ColorMode.RGB)
      cel:drawImage(srcImg, Point(-col * grid, -p.row * grid))
      sprite:newCel(layer, sprite.frames[f], cel, Point(0, 0))
      f = f + 1
    end
  end

  -- Tag the contiguous frame ranges (1-based, inclusive).
  local from = 1
  local summary = {}
  for _, p in ipairs(plan) do
    local to = from + p.frames - 1
    local t = sprite:newTag(from, to)
    t.name = p.name
    table.insert(summary, p.name .. "=" .. p.frames)
    from = to + 1
  end

  sprite:saveAs(outPath)
  print("wrote " .. outPath .. " (" .. grid .. "px, " .. #sprite.frames .. " frames: "
        .. table.concat(summary, " ") .. ")")
  src:close()
  sprite:close()
end

for _, sheet in ipairs(SHEETS) do
  build(sheet)
end
