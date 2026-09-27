RelationshipBonusData = {}
RelationshipBonusData.__index = RelationshipBonusData;

function RelationshipBonusData.New(reader)
    local self = {};
    setmetatable(self, RelationshipBonusData);

    self.serialNumber = reader:ReadUInt16();
    self.conditionType = reader:ReadByte();
    self.conditionValue = reader:ReadUInt16();
    self.bonusType = reader:ReadByte();
    self.propertiesId = reader:ReadByte();
    self.propertiesValue = reader:ReadUInt16();

    return self.serialNumber, self.conditionType, self.conditionValue, self.propertiesId, self;
end
