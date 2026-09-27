ImmortalArtDemonSlayingSlash = InheritsFromBaseAttackSkill();

function ImmortalArtDemonSlayingSlash:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Sky, 11120, 20, 1, 8, 8, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y + 60, 1, true, 30);
    self.interval = 0.01;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      me:PlaySound(target.colm, target.row);
      me:SetJump(target.nowPos.x, target.nowPos.y, false);
      me.roleController:SetAnimationForceId(ERolePose.Stand);
      me:SetBeh(EFightBeh.Rotate);
      me:SetSpeed(0.66);
      me:PlaySound(target.colm, target.row, Attack_Start);
      FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Sky, 11120, 120, 1, 8, 8, EEffectLightTracer.Curve, me.nowPos.x + 30, me.nowPos.y + 60, 40, true, 0, target.nowPos.x, target.nowPos.y + 60, 0.66);
      FightField.attack[self.humIdx].lights[1]:SetAlpha(10, 2, 20);
      FightField.attack[self.humIdx].lights[1]:SetResidual(3, 50, -10);
      self.step = 3;
    end
  elseif self.step == 3 then
    if me.arrive then
      me:SetJump(me.nowPos.x, me.nowPos.y - 60, false);
      FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Sky, 11121, 80, 1, 11, 11, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y + 60, 30, true, 40);
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky,11122, 80, 1, 12, 12, EEffectLightTracer.StandAni, target.nowPos.x + 10, target.nowPos.y, 30, true, 45);
      self.interval = 0.6;
      self.step = 4;
    end
  elseif self.step == 4 then
    if self:CheckInterval() then 
      FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Sky, 11123, 60, 1, 10, 10, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, true, 30);
      FightField.attack[self.humIdx]:SetLightWithOrder(5, EEffectLightDis.Sky, 11124, 100, 5, 15, 15, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 27, true, 80);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit, 2);

      if not target:IsInSealStatus() then
        target.roleController:SetIsDead(true);
        target:SetBeh(EFightBeh.Rotate);
        target:SetJump(target.nowPos.x, target.nowPos.y - 80, false);
      end

      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Ground, "Shadow2", 160, 1, 1, 1, EEffectLightTracer.Stand, target.nowPos.x, target.nowPos.y, 255, true, 35);
      self.step = 5;
    end
  elseif self.step == 5 then
    if target.arrive then
      me.roleController:SetIsJumping(true);
      local chipPos = me:GetChipPos();
      me:SetJump(chipPos.x, chipPos.y, true);
      me:SetSpeed(1);

      if not target:IsInSealStatus() then
        target:SetJump(target.nowPos.x, target.nowPos.y + 80, false);
      end
      self.step = 6;
    end
  elseif self.step == 6 then
    if target.arrive then
      me:PlayEffect(target.colm, target.row, ERolePose.Hit, 2);
      self.step = 7;
    end
  elseif self.step == 7 then
    if me.arrive then
      self.isLightEnd = true;
      self.step = 7;
    end
  end
end
