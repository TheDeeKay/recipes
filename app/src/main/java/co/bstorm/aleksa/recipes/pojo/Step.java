package co.bstorm.aleksa.recipes.pojo;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by aleksa on 7/29/16.
 * TODO
 */
@Table(name = "Steps")
public class Step extends Model{

    @Column(unique = true, onUniqueConflict = Column.ConflictAction.REPLACE)
    @Expose
    @SerializedName("id")
    private int remoteId;
    @Column
    @Expose
    @SerializedName("text")
    private String text;
    @Column
    @Expose
    @SerializedName("seq_num")
    private int sequenceIndex;
    @Column
    @Expose
    @SerializedName("timer")
    private int timer;
    @Column
    @Expose
    @SerializedName("timer_name")
    private String timerName;
    @Column
    @Expose
    @SerializedName("image_file_name")
    private String imageUrl;

    @Column
    private int recipeId;

    public Step() {
    }

    public int getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(int id) {
        this.remoteId = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(int sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public int getTimer() {
        return timer;
    }

    public void setTimer(int timer) {
        this.timer = timer;
    }

    public String getTimerName() {
        return timerName;
    }

    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(int recipeId) {
        this.recipeId = recipeId;
    }
}
