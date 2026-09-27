FlyFire = InheritsFromBaseAttackSkill();

function FlyFire:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Body, 10092, 50, 1, 10, 10, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 25, true, 20);
    self.interval = 0.5;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      local newPos = target:GetSpot(EFightSpot.Back, 60);
      me:SetMoment(newPos.x, newPos.y, false);
      me.roleController:FaceTo(target.nowPos);
      self.step = 3;
    end
  elseif self.step == 3 then
    if me.arrive then
      FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Body, 10092, 50, 1, 10, 10, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 25, true, 20);
      me.roleController:OneHandAttack();
      self.step = 4;
    end
  elseif self.step == 4 then
    if me.roleController.animationFrame == 1 then
      if target.party_Kind == EFightParty.Left then
        FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, 10149, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x - 60, target.nowPos.y, 25, true, 80);
      else
        FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, 10148, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x + 30, target.nowPos.y, 25, true, 120);
      end

      FightField.attack[self.humIdx].lights[2]:SetAlpha(40, -1, 40);
      FightField.attack[self.humIdx]:SetLightWithOrder(4, EEffectLightDis.Sky, 10133, 30, 1, 16, 16, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 25, true, 50);

      me:PlaySound(target.colm, target.row);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit, 2);
      self.step = 5;
    end
  elseif self.step == 5 then
    if me.roleController.animationFrame == 0 then
      local newPos = target:GetSpot(EFightSpot.Front, 60);
      me:SetMoment(newPos.x, newPos.y, false);
      me.roleController:FaceTo(target.nowPos);
      self.step = 6;
    end
  elseif self.step == 6 then
    if me.arrive then
      FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Body, 10092, 50, 1, 10, 10, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 35, true, 20);
      FightField.attack[self.humIdx].lights[2]:SetAlpha(35, -2, 20);
      me.roleController:OneHandAttack();
      me.roleController:RestartAnimation();
      self.step = 7;
    end
  elseif self.step == 7 then
    if me.roleController.animationFrame == 1 then
      if target.party_Kind == EFightParty.Left then
        FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, 10148, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x + 30, target.nowPos.y, 35, true, 120);
      else
        FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, 10149, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x - 60, target.nowPos.y, 35, true, 80);
      end

      me:PlaySound(target.colm, target.row);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit, 2);
      self.step = 8;
    end
  elseif self.step == 8 then
    if me.roleController.animationFrame == 0 then
      me:SetJump(FightField.chip[me.colm][me.row].x, FightField.chip[me.colm][me.row].y, true);
      me:SetSpeed(1);
      self.step = 9;
    end
  elseif self.step == 9 then
    if me.arrive then
      self.isLightEnd = true;
      self.step = 10;
    end
  end
end