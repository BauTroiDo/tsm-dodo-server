SilverShieldMe = InheritsFromBaseStatus();

function SilverShieldMe:Update()
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then
    newPos = me:RCtoSpot(me.colm, me.row);
    local status = me:GetStatus(self.statusKind);
    if me.party_Kind == EFightParty.Left then
      status:SetLightWithOrder(2, EEffectLightKind.Sky, 10501, 100, 15, 18, 18, EEffectLightTracer.Stand, newPos.x, newPos.y - 100, 30, true, 45);
      self:SetEffectLight(2, 2);
    elseif me.party_Kind == EFightParty.Right then
      status:SetLightWithOrder(2, EEffectLightKind.Sky, 10501, 100, 15, 18, 18, EEffectLightTracer.Stand, newPos.x, newPos.y - 100, 30, true, 45);
      self:SetEffectLight(2, 2);
    end
    self.step = 2;
  end
end

function SilverShieldMe:SetEffectLight(effectIdx, addColor)
  local effectColor = LuaHelper.GetColor(192, 192, 192, 255);
  local me = FightField.fightHum[self.humIdx];
  local status = me:GetStatus(self.statusKind);
  status.lights[effectIdx]:SetColor(effectColor);
  status.lights[effectIdx]:SetAddColor(addColor);
  status.lights[effectIdx]:SetColorMix(true);
  status.lights[effectIdx]:SetScale(Vector3.New(0.3, 0.3, 1));
end
