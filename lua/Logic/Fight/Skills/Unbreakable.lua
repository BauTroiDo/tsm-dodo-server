UnbreakableMe = InheritsFromBaseStatus();

function UnbreakableMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local status = me:GetStatus(self.statusKind);
  if self.step == 1 then
    status:SetLightWithOrder(0, EEffectLightKind.Sky, 20001, 100, 1, 12, 12, 
    EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y - 20, 30, true, 45);
    self.step = 2;
  end
end
