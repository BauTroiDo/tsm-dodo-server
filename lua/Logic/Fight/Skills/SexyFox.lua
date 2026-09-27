SexyFox = InheritsFromBaseAttackSkill();

function SexyFox:Update()
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];
  local attack = FightField.attack[self.humIdx];
  if self.step == 1 then
    attack:SetLightWithOrder(2, EEffectLightDis.Sky, 11097, 250, 1, 18, 18, EEffectLightTracer.StandAni, 0, 27, 25,
      false, 160);
    attack.lights[2]:SetScale(Vector3.New(1.2, 1.2, 1));
    self.interval = 4.5;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then
      self.isLightEnd = true;
    end
  end
end

SexyFoxMe = InheritsFromBaseStatus();
function SexyFoxMe:Update()
  local me = FightField.fightHum[self.humIdx];
  local newPos = me:RCtoSpot(me.colm, me.row);
  local status = me:GetStatus(self.statusKind);
  if self.step == 1 then
    me.status[self.statusKind]:SetLightWithOrder(1, EEffectLightKind.Status, 20059, 100, 1, 18, 18,
      EEffectLightTracer.Stand, newPos.x, newPos.y - 40, 80, true, 80);
    status.lights[1]:SetScale(Vector3.New(0.5, 0.5, 1));
    self.step = 2;
  end
end
