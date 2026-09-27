GodzillaEarthquake = InheritsFromBaseAttackSkill();

function GodzillaEarthquake:Update()
    local me = FightField.fightHum[self.humIdx];
    local target = FightField.fightHum[self.tarIdx];

    if self.step == 1 then
        me.roleController:SetAnimationForceId(ERolePose.Special);
        FightField.SetShake(true);
        me:PlaySound(target.colm, target.row, Attack_Start);
        self.interval = 0.2;
        self.step = 2;
    elseif self.step == 2 then
        if self:CheckInterval() then
            for i = 0, self.emyNum-1 do
                target = FightField.fightHum[ self.emyIdxAy[i] ];
                me:PlaySound(target.colm, target.row, Attack_Hit);
                me:PlayEffect(target.colm, target.row, ERolePose.Hit);
                FightField.attack[self.humIdx]:SetLightWithOrder( i , EEffectLightDis.Ground, 10103, 250, 1, 5, 5, EEffectLightTracer.Stand, target.nowPos.x + 20,
                        target.nowPos.y + 10, 255, false ,80);
            end
            self.interval = 1.8;
            self.step = 3;
        end
    elseif self.step == 3 then
        if self:CheckInterval() then
            FightField.SetShake(false);
            self.isLightEnd = true;
        end
    end
end