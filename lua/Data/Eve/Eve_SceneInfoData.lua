SceneInfoData = {}
SceneInfoData.__index = SceneInfoData

function SceneInfoData.New(reader) 
  local self = {};
  setmetatable(self, SceneInfoData);
  
  self.EveNo = reader:ReadUInt16();
  self.BackGroundNo = reader:ReadUInt16();
  self.EveFileName = reader:ReadUInt16();
  self.PlayerAppearX = reader:ReadInt32();
  self.PlayerAppearY = reader:ReadInt32();
  self.Direction = reader:ReadByte();
  self.AreaName = {};
  for i = 1, 21 do
   self.AreaName[i] = reader:ReadByte();
  end	
  self.LinkCount = reader:ReadUInt16();
  self.SceneEffect = reader:ReadByte();

  return self;
end