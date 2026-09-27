BattleRoyaleVocationData= {}
BattleRoyaleVocationData.__index = BattleRoyaleVocationData;
local this = BattleRoyaleVocationData;

function BattleRoyaleVocationData.New(reader)
  local self = {};
  setmetatable(self, BattleRoyaleVocationData);

  self.id = reader:ReadUInt16();
  self.name = string.Get(reader:ReadInt32());
  self.skillID = {};
  for i = 1, 2 do
    table.insert(self.skillID, reader:ReadUInt16());
  end
  return self.id, self;
end

function BattleRoyaleVocationData:GetSkill()
  if self.skill == nil then
    self.skill = {};
    for i, v in pairs(self.skillID) do
      table.insert(self.skill, sceneSkillDatas[v]);
    end
  end
  return self.skill;
end