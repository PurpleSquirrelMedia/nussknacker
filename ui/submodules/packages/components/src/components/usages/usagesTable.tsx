import type { ComponentUsageType } from "nussknackerUi/HttpService";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ScenarioCell } from "./scenarioCell";
import { Columns, TableViewData, TableWrapper } from "../tableWrapper";
import { createFilterRules, useFilterContext } from "../../common";
import { Pause, RocketLaunch } from "@mui/icons-material";
import { NodesCell } from "./nodesCell";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import Highlighter from "react-highlight-words";
import { Highlight } from "../utils";
import { GridRow, GridRowProps } from "@mui/x-data-grid";
import { BoxWithArchivedStyle } from "../../common/boxWithArchivedStyle";

function Highlighted({ value }: { value: string }): JSX.Element {
    const { getFilter } = useFilterContext<UsagesFiltersModel>();
    return (
        <Highlighter
            autoEscape
            textToHighlight={value.toString()}
            searchWords={getFilter("TEXT")?.toString().split(/\s/) || []}
            highlightTag={Highlight}
        />
    );
}

const isDeployed = (r: ComponentUsageType): boolean => (r.lastAction ? r.lastAction.action === "DEPLOY" : null);

export function UsagesTable(props: TableViewData<ComponentUsageType>): JSX.Element {
    const { data = [], isLoading } = props;
    const { t } = useTranslation();

    const columns = useMemo(
        (): Columns<ComponentUsageType> => [
            {
                field: "name",
                cellClassName: "noPadding stretch",
                headerName: t("table.usages.title.NAME", "Name"),
                flex: 3,
                minWidth: 160,
                renderCell: ScenarioCell,
                hideable: false,
            },
            {
                field: "isFragment",
                headerName: t("table.usages.title.IS_FRAGMENT", "Fragment"),
                valueGetter: ({ row }) => row.isSubprocess,
                type: "boolean",
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "processCategory",
                headerName: t("table.usages.title.PROCESS_CATEGORY", "Category"),
                renderCell: Highlighted,
                flex: 1,
            },
            {
                field: "createdAt",
                headerName: t("table.usages.title.CREATION_DATE", "Creation date"),
                type: "dateTime",
                flex: 2,
                renderCell: Highlighted,
                hide: true,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "createdBy",
                headerName: t("table.usages.title.CREATED_BY", "Author"),
                renderCell: Highlighted,
                flex: 1,
            },
            {
                field: "modificationDate",
                headerName: t("table.usages.title.MODIFICATION_DATE", "Modification date"),
                type: "dateTime",
                flex: 2,
                renderCell: Highlighted,
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "isDeployed",
                headerName: t("table.usages.title.IS_DEPLOYED", "Deployed"),
                valueGetter: ({ row }) => isDeployed(row),
                type: "boolean",
                renderCell: ({ value }) => {
                    if (value === null) {
                        return <></>;
                    }
                    if (!value) {
                        return <Pause color="disabled" />;
                    }
                    return <RocketLaunch color="warning" />;
                },
                sortingOrder: ["desc", "asc", null],
            },
            {
                field: "nodesId",
                headerName: t("table.usages.title.NODES_ID", "Nodes"),
                minWidth: 250,
                flex: 4,
                sortComparator: (v1: string[], v2: string[]) => v1.length - v2.length,
                renderCell: NodesCell,
                hideable: false,
                sortingOrder: ["desc", "asc", null],
            },
        ],
        [t],
    );

    const filterRules = useMemo(
        () =>
            createFilterRules<ComponentUsageType, UsagesFiltersModel>({
                SHOW_ARCHIVED: (row, filter) => filter || !row.isArchived,
                TEXT: (row, filter) =>
                    !filter?.toString().length ||
                    columns
                        .map(({ field }) => row[field]?.toString().toLowerCase())
                        .filter(Boolean)
                        .some((value) => value.includes(filter.toString().toLowerCase())),
            }),
        [columns],
    );

    return (
        <TableWrapper<ComponentUsageType, UsagesFiltersModel>
            columns={columns}
            data={data}
            isLoading={isLoading}
            filterRules={filterRules}
            components={{
                Row: (props: GridRowProps) => <BoxWithArchivedStyle isArchived={props.row.isArchived} component={GridRow} {...props} />,
            }}
        />
    );
}
