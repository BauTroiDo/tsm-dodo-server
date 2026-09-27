FieldEffectData = {}
FieldEffectData.__index = FieldEffectData;

local this = FieldEffectData;

function FieldEffectData.New(reader)
  local self = {};
  setmetatable(self, FieldEffectData);
  
  self.id = reader:ReadUInt16(); --狀態ID
  self.order = reader:ReadByte(); --順序
  self.effectId = reader:ReadUInt16(); --狀態編號
  self.round = reader:ReadByte(); --持續回合
  self.kind = reader:ReadUInt16(); --種類
  self.value = reader:ReadInt32(); --數值
  local icon = reader:ReadUInt16(); --icon編號
  if icon ~= 0 then
      self.image = string.Concat("icon", icon);
  end
  self.effect = {};
  self.effect.Id = reader:ReadUInt16(); --特效編號
  self.effect.Interval = reader:ReadByte(); --特效更新間隔(毫秒)
  self.effect.StartFrame = reader:ReadByte(); --特效起始張數
  self.effect.EndFrame = reader:ReadByte(); --特效結束張數
  self.effect.TotalFrame = reader:ReadByte(); --特效總張數
  self.effect.Scale = reader:ReadUInt16() * 0.001; --特效縮放(千分比)
  self.effect.Offset = reader:ReadInt16(); --特效高度位移
  local circle = reader:ReadByte(); --特效循環 0不循環 1循環
  self.effect.Circle = circle == 1;
  return self.id, self.order, self;
end

function FieldEffectData:GetAttribute()
  if self.kind == 1 then
      return EAttribute.Int
  elseif self.kind == 2 then
      return EAttribute.Atk
  elseif self.kind == 3 then
      return EAttribute.Def
  elseif self.kind == 4 then
      return EAttribute.Hpx
  elseif self.kind == 5 then
      return EAttribute.Spx
  elseif self.kind == 6 then
      return EAttribute.Agi
  elseif self.kind == 13 then
      return EAttribute.MaxHp
  elseif self.kind == 15 then
      return EAttribute.MaxSp
  end
end

function FieldEffectData:GetEffectKind()
  if Contains(self.effectId, 184, 257) then
    return EBattleRoyaleEffectType.Percentage
  elseif Contains(self.effectId, 186, 256) then
    return EBattleRoyaleEffectType.Fixed
  end
end

function FieldEffectData:GetValue()
  if Contains(self.effectId, 184, 186) then
    return -self.value
  else
    return self.value
  end
end