package de.alexanderwodarz.code.web.rest;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

@Setter
@Getter
public abstract class BodyModel {

    private JSONObject obj;

}
