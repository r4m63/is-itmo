package ru.itmo.isitmolab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GridTableRequest {
    @NotNull
    public Integer startRow; // индекс первой строки, которую нужно вернуть
    @NotNull
    public Integer endRow; // индекс строки за пределами окна
    public List<GridTableSortModel> sortModel; // список правил сортировки
    // Ключ -- colId ("name", "type", "admin.id", "coordinates.x")
    // Значение -- объект с полями, зависящими от типа фильтра (text, number, date, set)
    public Map<String, Object> filterModel; // идентификатор_колонки -> описание_фильтра
}

/*
{
    "startRow": 0,
    "endRow": 50,
    "sortModel": [
        {"colId": "creationTime", "sort": "desc"},
        {"colId": "id", "sort": "desc"}
    ],
    "filterModel": {
        "type": {
            "filterType": "text",
            "type": "equals",
            "filter": "CAR"
        },
        "enginePower": {
            "filterType": "number",
            "type": "inRange",
            "filter": 100,
            "filterTo": 200
        },
        "creationTime": {
            "filterType": "date",
            "type": "inRange",
            "dateFrom": "2025-10-01",
            "dateTo": "2025-10-20"
        },
        "fuelType": {
            "filterType": "set",
            "values": ["KEROSENE","NUCLEAR"]
        }
    }
}
*/


