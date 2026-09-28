BattleRoyaleSceneData= {}
BattleRoyaleSceneData.__index = BattleRoyaleSceneData;
local this = BattleRoyaleSceneData;
local portalSize = 60;

function BattleRoyaleSceneData.New(reader)
  local self = {};
  setmetatable(self, BattleRoyaleSceneData);
  self.id = reader:ReadUInt16();
  self.sceneID = reader:ReadUInt16();

  self.doors = {};
  for index = 1, 4 do
    local door = {};
    local x = reader:ReadInt32();
    local y = reader:ReadInt32();
    door.valid = x~=0 and y~=0;

    door.id = index;
    door.rect = Rect.New();
    door.rect.x = x - portalSize / 2;
    door.rect.y = y - portalSize / 2;
    door.rect.width = portalSize;
    door.rect.height = portalSize;
    door.imgInfo = {};
    door.imgInfo.imageKind = 1;
    door.imgInfo.position = {};
    door.imgInfo.position.x = x;
    door.imgInfo.position.y = y;
    table.insert(self.doors, door);
  end

  self.entries = {};
  for index = 1, 4 do
    local entry = {};
    entry.x = reader:ReadInt32();
    entry.y = reader:ReadInt32();
    table.insert(self.entries, entry);
  end

  self.pillar = {};
  self.pillar.id = reader:ReadUInt16();
  self.pillar.x = reader:ReadInt32();
  self.pillar.y = reader:ReadInt32();

  self.normalMob = {};
  self.normalMob = this.ReadMob(reader, 4);

  self.killerMob = {};
  self.killerMob = this.ReadMob(reader, 1);

  return self.sceneID, self;
end

function BattleRoyaleSceneData.ReadMob(reader, count)
  local mob = {};
  mob.ID = {};
  for index = 1, count do
    table.insert(mob.ID, reader:ReadUInt16());
  end
  mob.position = {};
  mob.position.x = reader:ReadInt32();
  mob.position.y = reader:ReadInt32();
  mob.range = reader:ReadUInt16();
  mob.speed = reader:ReadByte();
  return mob;
end