SceneSkillData= {}
SceneSkillData.__index = SceneSkillData;
local this = SceneSkillData;

function SceneSkillData.New(reader)
  local self = {};
  setmetatable(self, SceneSkillData);

  self.name = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.id = reader:ReadUInt16();
  self.target = reader:ReadByte();
  self.coolDown = reader:ReadUInt16();
  self.vocation = reader:ReadByte();
  self.description = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  local iconIndex = reader:ReadUInt16();
  self.icon = string.Concat("icon", iconIndex);
  self.effect = {};
  self.effect.Id = reader:ReadUInt16(); --特效編號
  self.effect.Interval = reader:ReadByte(); --特效更新間隔(毫秒)
  self.effect.StartFrame = reader:ReadByte(); --特效起始張數
  self.effect.EndFrame = reader:ReadByte(); --特效結束張數
  self.effect.TotalFrame = reader:ReadByte(); --特效總張數
  self.effect.Scale = reader:ReadUInt16() * 0.001; --特效縮放(千分比)
  self.effect.Offset = reader:ReadInt16(); --特效高度位移
  return self.id, self;
end
