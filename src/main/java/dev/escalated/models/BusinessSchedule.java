package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_business_schedules")
public class BusinessSchedule extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(name = "monday_start")
    private String mondayStart = "09:00";

    @Column(name = "monday_end")
    private String mondayEnd = "17:00";

    @Column(name = "tuesday_start")
    private String tuesdayStart = "09:00";

    @Column(name = "tuesday_end")
    private String tuesdayEnd = "17:00";

    @Column(name = "wednesday_start")
    private String wednesdayStart = "09:00";

    @Column(name = "wednesday_end")
    private String wednesdayEnd = "17:00";

    @Column(name = "thursday_start")
    private String thursdayStart = "09:00";

    @Column(name = "thursday_end")
    private String thursdayEnd = "17:00";

    @Column(name = "friday_start")
    private String fridayStart = "09:00";

    @Column(name = "friday_end")
    private String fridayEnd = "17:00";

    @Column(name = "saturday_start")
    private String saturdayStart;

    @Column(name = "saturday_end")
    private String saturdayEnd;

    @Column(name = "sunday_start")
    private String sundayStart;

    @Column(name = "sunday_end")
    private String sundayEnd;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "businessSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Holiday> holidays = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getMondayStart() {
        return mondayStart;
    }

    public void setMondayStart(String mondayStart) {
        this.mondayStart = mondayStart;
    }

    public String getMondayEnd() {
        return mondayEnd;
    }

    public void setMondayEnd(String mondayEnd) {
        this.mondayEnd = mondayEnd;
    }

    public String getTuesdayStart() {
        return tuesdayStart;
    }

    public void setTuesdayStart(String tuesdayStart) {
        this.tuesdayStart = tuesdayStart;
    }

    public String getTuesdayEnd() {
        return tuesdayEnd;
    }

    public void setTuesdayEnd(String tuesdayEnd) {
        this.tuesdayEnd = tuesdayEnd;
    }

    public String getWednesdayStart() {
        return wednesdayStart;
    }

    public void setWednesdayStart(String wednesdayStart) {
        this.wednesdayStart = wednesdayStart;
    }

    public String getWednesdayEnd() {
        return wednesdayEnd;
    }

    public void setWednesdayEnd(String wednesdayEnd) {
        this.wednesdayEnd = wednesdayEnd;
    }

    public String getThursdayStart() {
        return thursdayStart;
    }

    public void setThursdayStart(String thursdayStart) {
        this.thursdayStart = thursdayStart;
    }

    public String getThursdayEnd() {
        return thursdayEnd;
    }

    public void setThursdayEnd(String thursdayEnd) {
        this.thursdayEnd = thursdayEnd;
    }

    public String getFridayStart() {
        return fridayStart;
    }

    public void setFridayStart(String fridayStart) {
        this.fridayStart = fridayStart;
    }

    public String getFridayEnd() {
        return fridayEnd;
    }

    public void setFridayEnd(String fridayEnd) {
        this.fridayEnd = fridayEnd;
    }

    public String getSaturdayStart() {
        return saturdayStart;
    }

    public void setSaturdayStart(String saturdayStart) {
        this.saturdayStart = saturdayStart;
    }

    public String getSaturdayEnd() {
        return saturdayEnd;
    }

    public void setSaturdayEnd(String saturdayEnd) {
        this.saturdayEnd = saturdayEnd;
    }

    public String getSundayStart() {
        return sundayStart;
    }

    public void setSundayStart(String sundayStart) {
        this.sundayStart = sundayStart;
    }

    public String getSundayEnd() {
        return sundayEnd;
    }

    public void setSundayEnd(String sundayEnd) {
        this.sundayEnd = sundayEnd;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Holiday> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<Holiday> holidays) {
        this.holidays = holidays;
    }
}
