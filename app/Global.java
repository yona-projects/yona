import java.util.List;
import java.util.Map;

import models.User;

import com.avaje.ebean.Ebean;

import play.Application;
import play.GlobalSettings;
import play.libs.Yaml;

public class Global extends GlobalSettings {
	public void onStart(Application app) {
		InitialData.insert(app);
	}

	static class InitialData {

		public static void insert(Application app) {
			if (Ebean.find(User.class).findRowCount() == 0) {
				@SuppressWarnings("unchecked")
				Map<String, List<Object>> all = (Map<String, List<Object>>) Yaml
						.load("initial-data.yml");

				Ebean.save(all.get("users"));
				Ebean.save(all.get("articles"));
				Ebean.save(all.get("issues"));
				Ebean.save(all.get("projects"));
			}
		}

	}

	public void onStop(Application app) {
	}
}
