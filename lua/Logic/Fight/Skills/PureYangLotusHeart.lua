PureYangLotusHeartMe = InheritsFromBaseStatus();

function PureYangLotusHeartMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local status = me:GetStatus(self.statusKind);
  if self.step == 1 then
    status:SetLightWithOrder(1, EEffectLightKind.Sky, 11126, 150, 1, 15, 15, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y + 50, 30, true);
    self.step = 2;
  end
end
