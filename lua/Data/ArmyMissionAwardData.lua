ArmyMissionAwardData = {}
ArmyMissionAwardData.__index = ArmyMissionAwardData;

function ArmyMissionAwardData.New(reader)
  local self = {};
  setmetatable(self, ArmyMissionAwardData);
  
  self.index = reader:ReadByte();         --流水號
  self.stringId = reader:ReadUInt32();        --字串
  self.roleCount = reader:ReadUInt16();          --種類
  self.value = reader:ReadUInt32();      --數值
  self.item = reader:ReadUInt16();          --物品編號
  self.quant = reader:ReadUInt32();       --物品數量
  self.missionLimit = reader:ReadInt32();
  
  return self.index, self;
end
