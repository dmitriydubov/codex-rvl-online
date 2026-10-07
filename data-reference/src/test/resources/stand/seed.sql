-- Стенд потребителя: синтетическое сырьё 1С:УСК для сервиса Договоров.
--
-- Строки ложатся в inbound_message так, как их положил бы приём из брокера: конверт
-- {data, metadata} целиком, статус NEW. Дальше их штатно разбирает планировщик
-- сервиса — справочники получаются тем же путём, что из 1С. В таблицы справочников
-- напрямую ничего не пишется.
--
-- Порядок разбора — received_at, поэтому родители идут раньше детей:
-- ТЗ-3 (санаторий, страхователь) → ТЗ-2 (договор ДМС) → ТЗ-6 (программа) → ТЗ-1 (полис).
-- Время — несколько секунд до момента закладки, а не фиксированная дата: тело полиса
-- стирается через 30 суток после received_at, и полис со старой датой ушёл бы в отказ,
-- не разобравшись. now() — время начала транзакции, одно на весь INSERT, поэтому
-- смещения задают порядок однозначно.
--
-- Сроки полиса и договора — многолетние: GET их не фильтруют, но сервису Договоров
-- для выпуска нужен действующий полис.
--
-- Повторная закладка ничего не меняет: ключ — message_id, конфликт пропускается.
--
-- Все значения синтетические: GUID сгенерированы, ФИО и адреса вымышлены, почта —
-- на example.com. GUID вида деятельности РВЛ и тип продукта ДМС — значения конфигурации
-- (application.yml), без них санаторий и договор не пройдут фильтры потоков.
--
-- Файл читают и docker-compose.yml (одноразовый psql профиля stand), и ConsumerStandTest:
-- поэтому только SQL, без метакоманд psql, и без точки с запятой внутри строк.

INSERT INTO inbound_message (message_id, stream, source_queue, payload, status, attempt, received_at)
VALUES
    ('404fa95b-18c4-4ca2-b57a-083c42204acd', 'CONTRAGENTS', '1c-usk_to_rvl-online_contragents-ul-changes',
     '{"data": {"guid": "b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c", "thisIsLPU": true, "deleteMark": false,
                "Наименование": "Санаторий «Стенд»",
                "НаименованиеПолное": "Общество с ограниченной ответственностью «Санаторий Стенд»",
                "ИНН": "7700000001", "КПП": "770001001", "Телефон": "+7 000 000-00-01",
                "EMAIL": "sanatorium@example.com",
                "typesActivities": [{"guidTypeActivities": "f3876c53-c7d8-4620-b364-9538164ba5d4"}]},
       "metadata": {"message-id": "404fa95b-18c4-4ca2-b57a-083c42204acd", "topic": "",
                    "producer": "1c-usk", "creation-timestamp": "2026-01-01T00:00:01"}}',
     'NEW', 0, now() - interval '5 seconds'),

    ('9d180fa9-eefc-45e2-bf18-f6f275710a84', 'CONTRAGENTS', '1c-usk_to_rvl-online_contragents-ul-changes',
     '{"data": {"guid": "d23f3088-f547-4755-b93e-ae192a89d166", "thisIsLPU": false, "deleteMark": false,
                "Наименование": "ООО «Тестовый страхователь»",
                "НаименованиеПолное": "Общество с ограниченной ответственностью «Тестовый страхователь»",
                "ИНН": "7700000002", "КПП": "770001002"},
       "metadata": {"message-id": "9d180fa9-eefc-45e2-bf18-f6f275710a84", "topic": "",
                    "producer": "1c-usk", "creation-timestamp": "2026-01-01T00:00:02"}}',
     'NEW', 0, now() - interval '4 seconds'),

    ('16136d1e-39fe-4b1f-9eca-bb90d7f2c5db', 'INSURANCE_CONTRACTS', '1c-usk_to_rvl-online_contracts-changes',
     '{"data": {"contractId": "77ff17b5-e659-4720-b57f-e8a19c00fcea", "number": "СТЕНД-ДМС-0001",
                "productType": "Личное страхование", "productCode": "000000001",
                "dateBeginISO": "2026-01-01T00:00:00", "dateEndISO": "3000-01-01T00:00:00", "notActive": false,
                "contragentList": [{"rolesList": [{"roleName": "Страхователь", "roleId": "Страхователь"}],
                                    "contragent": {"type": "Юридическое лицо",
                                                   "fullName": "ООО «Тестовый страхователь»",
                                                   "guid": "d23f3088-f547-4755-b93e-ae192a89d166"}}]},
       "metadata": {"message-id": "16136d1e-39fe-4b1f-9eca-bb90d7f2c5db", "topic": "",
                    "producer": "1c-usk", "creation-timestamp": "2026-01-01T00:00:03"}}',
     'NEW', 0, now() - interval '3 seconds'),

    ('a1960bab-85db-47b4-bc2b-0cc4beb976a1', 'PROGRAMS', '1c-usk_to_rvl-online_programms-changes',
     '{"data": {"id": "995448e7-dd5b-499a-8a4e-97b363d06dd3", "rvl": true, "deleteMark": false,
                "name": "Стенд: восстановительное лечение", "typeProgramm": "СКЛ",
                "service": [{"idLPU": "b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c",
                             "idTreatment": "95ca15cf-daed-4c11-9b70-8e5366a97295",
                             "beginDate": "2026-01-01", "endDate": "3000-01-01",
                             "numberLayer": 1, "directAttachment": true, "percentCopayment": 10.00,
                             "r": true, "d": false}]},
       "metadata": {"message-id": "a1960bab-85db-47b4-bc2b-0cc4beb976a1", "topic": "",
                    "producer": "1c-usk", "creation-timestamp": "2026-01-01T00:00:04"}}',
     'NEW', 0, now() - interval '2 seconds'),

    ('fb9a7691-a49f-40a7-a675-715a4ba94617', 'POLICIES', '1c-usk_to_rvl-online_policies',
     '{"data": {"id": "9e6ee30d-2275-417d-a44c-039d3b8c2ac5", "idContract": "77ff17b5-e659-4720-b57f-e8a19c00fcea",
                "number": "СТЕНД-0001", "deleteMark": false,
                "lastName": "Тестов", "firstName": "Тест", "middleName": "Тестович",
                "dateOfBirth": "1990-01-01", "gender": "М",
                "extProperties": [{"propertyName": "Адрес элпочты застрахованного",
                                   "value": {"value": "insured@example.com"}}],
                "header": [{"rowNumber": 1, "beginDate": "2026-01-01", "endDate": "2099-12-31", "availability": true}],
                "programms": [{"idProgramm": "995448e7-dd5b-499a-8a4e-97b363d06dd3",
                               "beginDate": "2026-01-01", "endDate": "2099-12-31",
                               "amount": 500000.00, "premium": 12000.00, "limit": 150000.00,
                               "availability": true}],
                "services": [{"idProgramm": "995448e7-dd5b-499a-8a4e-97b363d06dd3", "layerNumber": 1,
                              "copayment": 20.00, "beginDate": "2026-01-01", "endDate": "2099-12-31",
                              "availability": true}]},
       "metadata": {"message-id": "fb9a7691-a49f-40a7-a675-715a4ba94617", "topic": "",
                    "producer": "1c-usk", "creation-timestamp": "2026-01-01T00:00:05"}}',
     'NEW', 0, now() - interval '1 second')

ON CONFLICT (message_id) DO NOTHING;
