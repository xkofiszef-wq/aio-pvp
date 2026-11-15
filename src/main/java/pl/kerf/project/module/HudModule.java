package pl.kerf.project.module;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Element HUD-u, który można przesuwać i skalować.
 *
 * <p>Pozycja to kotwica na ekranie (ułamek 0..1), przesunięcie w pikselach GUI oraz pivot, czyli punkt
 * elementu (ułamek 0..1) przypinany do kotwicy. Dzięki temu element przy prawej krawędzi albo obok
 * hotbara rośnie od punktu zaczepienia i nie rozjeżdża się przy innej rozdzielczości, skali GUI
 * ani gdy zmienia się jego zawartość.
 */
public abstract class HudModule extends Module {
	private final float defAx;
	private final float defAy;
	private final float defPx;
	private final float defPy;
	private final float defOx;
	private final float defOy;
	public float ax;
	public float ay;
	public float px;
	public float py;
	public float ox;
	public float oy;
	public float scale = 1.0F;
	/** Ostatnio narysowany prostokąt, w pikselach GUI. */
	public float boundsX;
	public float boundsY;
	public float boundsW;
	public float boundsH;

	protected HudModule(String id, String name, String description, boolean defEnabled, float ax, float ay, float px, float py, float ox, float oy) {
		super(id, name, description, Category.HUD, defEnabled);
		this.defAx = this.ax = ax;
		this.defAy = this.ay = ay;
		this.defPx = this.px = px;
		this.defPy = this.py = py;
		this.defOx = this.ox = ox;
		this.defOy = this.oy = oy;
	}

	/** Czy element da się przesuwać w edytorze. */
	public boolean movable() {
		return true;
	}

	/** Szerokość treści przed skalowaniem. */
	public abstract int width(MinecraftClient client, boolean editing);

	public abstract int height(MinecraftClient client, boolean editing);

	/**
	 * O ile tło (np. vanillowa ramka slotu) wystaje poza treść, w pikselach przed skalowaniem. Treść jest
	 * pozycjonowana tak samo w każdym stylu, więc przełączenie stylu nie przesuwa przedmiotów.
	 */
	public int margin() {
		return 0;
	}

	/** Czy element da się narysować w podglądzie menu (rysuje się przez {@link #draw}). */
	public boolean hasPreview() {
		return true;
	}

	/**
	 * Rysuje element w podglądzie menu, wyśrodkowany i dopasowany do prostokąta. Używa przykładowych danych
	 * (tryb edycji), więc coś widać nawet bez zbroi czy efektów. Wymaga świata (czyta dane gracza).
	 */
	public void renderPreview(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
		this.renderPreview(context, client, x, y, width, height, 2.0F);
	}

	/** Jak wyżej, z ograniczeniem powiększenia (małe kafelki pokazują element w naturalnym rozmiarze). */
	public void renderPreview(DrawContext context, MinecraftClient client, int x, int y, int width, int height, float maxScale) {
		int w = this.width(client, true);
		int h = this.height(client, true);
		int m = this.margin();
		if (w <= 0 || h <= 0) {
			return;
		}

		float fit = Math.min(maxScale, Math.min((width - 8.0F) / (w + 2 * m), (height - 8.0F) / (h + 2 * m)));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x + (width - w * fit) / 2.0F, y + (height - h * fit) / 2.0F);
		context.getMatrices().scale(fit, fit);
		this.draw(context, client, client.getRenderTickCounter(), true);
		context.getMatrices().popMatrix();
	}

	/** Rysuje treść w lokalnych współrzędnych (0,0 = lewy górny róg). */
	protected abstract void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing);

	public void render(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		int w = this.width(client, editing);
		int h = this.height(client, editing);
		this.boundsW = w * this.scale;
		this.boundsH = h * this.scale;
		this.boundsX = context.getScaledWindowWidth() * this.ax + this.ox - this.boundsW * this.px;
		this.boundsY = context.getScaledWindowHeight() * this.ay + this.oy - this.boundsH * this.py;
		// Przy małym oknie albo dużej skali GUI element (razem z ramką) nie może wyjechać poza ekran.
		float m = this.margin() * this.scale;
		this.boundsX = Math.max(m, Math.min(context.getScaledWindowWidth() - this.boundsW - m, this.boundsX));
		this.boundsY = Math.max(m, Math.min(context.getScaledWindowHeight() - this.boundsH - m, this.boundsY));
		if (w <= 0 || h <= 0) {
			return;
		}

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(this.boundsX, this.boundsY);
		context.getMatrices().scale(this.scale, this.scale);
		ownLayer(context);
		this.draw(context, client, tickCounter, editing);
		context.getMatrices().popMatrix();
	}

	/**
	 * Własna warstwa bazowa GUI dla elementu. GUI dla każdego nowego kształtu przeszukuje od góry wszystkie warstwy
	 * aż do bazowej, szukając czegoś, z czym się nakłada; bez tego każdy przedmiot/napis elementu przeglądał cały HUD
	 * (vanilla + inne elementy moda). Pomiar JFR: to był główny koszt FPS moda. Rysowanie i kolejność bez zmian.
	 */
	protected static void ownLayer(DrawContext context) {
		context.createNewRootLayer();
	}

	/**
	 * Ustawia lewy górny róg. Kotwica to tercja ekranu, w której leży środek elementu; w środkowej tercji
	 * pivot wskazuje stronę bliższą środka ekranu, żeby element obok hotbara rósł od niego na zewnątrz.
	 */
	public void moveTo(float x, float y, int screenW, int screenH) {
		float m = this.margin() * this.scale;
		x = Math.max(m, Math.min(screenW - this.boundsW - m, x));
		y = Math.max(m, Math.min(screenH - this.boundsH - m, y));
		float centerX = (x + this.boundsW / 2.0F) / screenW;
		this.ax = third(centerX);
		this.ay = third((y + this.boundsH / 2.0F) / screenH);
		this.px = this.ax != 0.5F ? this.ax : centerX < 0.47F ? 1.0F : centerX > 0.53F ? 0.0F : 0.5F;
		this.py = this.ay;
		this.ox = x + this.boundsW * this.px - screenW * this.ax;
		this.oy = y + this.boundsH * this.py - screenH * this.ay;
		this.boundsX = x;
		this.boundsY = y;
	}

	private static float third(float fraction) {
		return fraction < 1.0F / 3.0F ? 0.0F : fraction > 2.0F / 3.0F ? 1.0F : 0.5F;
	}

	public void setScale(float scale) {
		this.scale = Math.max(0.5F, Math.min(3.0F, Math.round(scale * 20.0F) / 20.0F));
	}

	public void resetPosition() {
		this.ax = this.defAx;
		this.ay = this.defAy;
		this.px = this.defPx;
		this.py = this.defPy;
		this.ox = this.defOx;
		this.oy = this.defOy;
		this.scale = 1.0F;
	}

	@Override
	public void reset() {
		super.reset();
		this.resetPosition();
	}

	@Override
	public JsonObject write() {
		JsonObject json = super.write();
		json.addProperty("ax", this.ax);
		json.addProperty("ay", this.ay);
		json.addProperty("px", this.px);
		json.addProperty("py", this.py);
		json.addProperty("ox", this.ox);
		json.addProperty("oy", this.oy);
		json.addProperty("scale", this.scale);
		return json;
	}

	@Override
	public void read(JsonObject json) {
		super.read(json);
		if (json.has("ax") && json.has("ay") && json.has("px") && json.has("py") && json.has("ox") && json.has("oy")) {
			this.ax = json.get("ax").getAsFloat();
			this.ay = json.get("ay").getAsFloat();
			this.px = json.get("px").getAsFloat();
			this.py = json.get("py").getAsFloat();
			this.ox = json.get("ox").getAsFloat();
			this.oy = json.get("oy").getAsFloat();
		}

		if (json.has("scale")) {
			this.setScale(json.get("scale").getAsFloat());
		}
	}
}
