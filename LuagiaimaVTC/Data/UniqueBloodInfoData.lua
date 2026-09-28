UniqueBloodInfoData = {}
UniqueBloodInfoData.__index = UniqueBloodInfoData;

function UniqueBloodInfoData.New(reader)
  local self = {};
  setmetatable(self, UniqueBloodInfoData);

  self.id = reader:ReadUInt16();
  self.mainElement = reader:ReadByte();
  self.lv1Att = reader:ReadUInt16();
  self.lvUpAddValue = reader:ReadUInt16();
  self.subElementLvUp = reader:ReadByte();
  self.subElementGroup = reader:ReadUInt16();
  self.subElementAdd = reader:ReadUInt16();
  self.kind = reader:ReadByte();
  self.breakUpTime = reader:ReadByte();
  self.upgradeGroup = reader:ReadByte();
  self.styleID = reader:ReadByte();
  self.decomposePiece = reader:ReadUInt16();

  return self.id, self;
end