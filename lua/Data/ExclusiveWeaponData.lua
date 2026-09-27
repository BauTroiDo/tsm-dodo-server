ExclusiveWeaponData = {};
ExclusiveWeaponData.__index = ExclusiveWeaponData;

function ExclusiveWeaponData.New(reader)
  local self = {};
  setmetatable(self, ExclusiveWeaponData);

  local id = reader:ReadUInt16();
  local lv = reader:ReadByte();
  self.skillId = reader:ReadUInt16();
  self.skillLv = reader:ReadByte();
  self.att = {};
  for i = 1, 2 do 
    self.att[i] = {};
    self.att[i].kind = reader:ReadUInt16();
    self.att[i].value = reader:ReadUInt32();
  end
  self.element = reader:ReadByte();
  self.elementValue = reader:ReadUInt32();
  self.plusAtt = {};
  self.plusAtt.kind = reader:ReadUInt16();
  self.plusAtt.value = reader:ReadUInt32();
  return id, lv,self;
end